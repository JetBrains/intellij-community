/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimedReference;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.ref.SoftReference;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarHandler implements FileSystemInterface {
  @NonNls private static final String JARS_FOLDER = "jars";

  private final Object lock = new Object();
  private final TimedReference<ZipFile> myZipFile = new TimedReference<ZipFile>(null);
  private final JarFileSystemImpl myFileSystem;
  private final String myBasePath;
  private SoftReference<Map<String, EntryInfo>> myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(null);

  private static class EntryInfo {
    public EntryInfo(final String shortName, final EntryInfo parent, final boolean directory) {
      this.shortName = new String(shortName);
      this.parent = parent;
      isDirectory = directory;
    }

    final boolean isDirectory;
    private final String shortName;
    final EntryInfo parent;
  }

  public JarHandler(final JarFileSystemImpl fileSystem, String path) {
    myFileSystem = fileSystem;
    myBasePath = path;
  }

  public void refreshLocalFileForJar() {
    NewVirtualFile localJarFile = (NewVirtualFile)LocalFileSystem.getInstance().refreshAndFindFileByPath(myBasePath);
    if (localJarFile != null) {
      localJarFile.markDirty();
    }
  }

  public void dispose() {
  }

  @Nullable
  public VirtualFile markDirty() {
    synchronized (lock) {
      myRelPathsToEntries.clear();
      myZipFile.set(null);

      final NewVirtualFile root = (NewVirtualFile)
        JarFileSystem.getInstance().findFileByPath(myBasePath + JarFileSystem.JAR_SEPARATOR);
      if (root != null) {
        root.markDirty();
        return root;
      }
      return null;
    }
  }

  @NotNull
  private Map<String, EntryInfo> initEntries() {
    synchronized (lock) {
      Map<String, EntryInfo> map = myRelPathsToEntries.get();
      if (map == null) {
        final ZipFile zip = getZip();

        map = new THashMap<String, EntryInfo>();
        if (zip != null) {
          map.put("", new EntryInfo("", null, true));
          final Enumeration<? extends ZipEntry> entries = zip.entries();
          while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final boolean isDirectory = name.endsWith("/");
            getOrCreate(isDirectory ? name.substring(0, name.length() - 1) : name, isDirectory, map);
          }

          myRelPathsToEntries = new SoftReference<Map<String, EntryInfo>>(map);
        }
      }
      return map;
    }
  }

  private static EntryInfo getOrCreate(String entryName, boolean isDirectory, Map<String, EntryInfo> map) {
    EntryInfo info = map.get(entryName);
    if (info == null) {
      int idx = entryName.lastIndexOf('/');
      final String parentEntryName = idx > 0 ? entryName.substring(0, idx) : "";
      String shortName = idx > 0 ? entryName.substring(idx + 1) : entryName;
      if (".".equals(shortName)) return getOrCreate(parentEntryName, true, map);

      info = new EntryInfo(shortName, getOrCreate(parentEntryName, true, map), isDirectory);
      map.put(entryName, info);
    }

    return info;
  }

  public String[] list(final VirtualFile file) {
    synchronized (lock) {
      EntryInfo parentEntry = getEntryInfo(file);

      Set<String> names = new HashSet<String>();
      for (EntryInfo info : getEntriesMap().values()) {
        if (info.parent == parentEntry) {
          names.add(info.shortName);
        }
      }

      return ArrayUtil.toStringArray(names);
    }
  }

  private EntryInfo getEntryInfo(final VirtualFile file) {
    String parentPath = getRelativePath(file);
    return getEntriesMap().get(parentPath);
  }

  private String getRelativePath(final VirtualFile file) {
    final String path = file.getPath().substring(myBasePath.length() + 1);
    return path.startsWith("/") ? path.substring(1) : path;
  }

  public File getMirrorFile(File originalFile) {
    if (!myFileSystem.isMakeCopyOfJar(originalFile) || !originalFile.exists()) return originalFile;

    String folderPath = PathManager.getSystemPath() + File.separatorChar + JARS_FOLDER;
    if (!new File(folderPath).exists()) {
      if (!new File(folderPath).mkdirs()) {
        return originalFile;
      }
    }

    String fileName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    final File mirror = new File(folderPath, fileName);

    if (!mirror.exists() || Math.abs(originalFile.lastModified() - mirror.lastModified()) > 2000) {
      return copyToMirror(originalFile, mirror);
    }

    return mirror;
  }

  private File copyToMirror(final File original, final File mirror) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();
    if (progress != null){
      progress.pushState();
      progress.setText(VfsBundle.message("jar.copy.progress", original.getPath()));
      progress.setFraction(0);
    }

    try{
      FileUtil.copy(original, mirror);
    }
    catch(final IOException e){
      final String path1 = original.getPath();
      final String path2 = mirror.getPath();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(VfsBundle.message("jar.copy.error.message", path1, path2, e.getMessage()), VfsBundle.message("jar.copy.error.title"),
                                     Messages.getErrorIcon());
        }
      }, ModalityState.NON_MODAL);

      myFileSystem.setNoCopyJarForPath(path1);
      return original;
    }

    if (progress != null){
      progress.popState();
    }

    return mirror;
  }

  @Nullable
  public ZipFile getZip() {
    ZipFile zip = myZipFile.get();
    if (zip == null) {
      try {
        zip = new ZipFile(getMirrorFile(getOriginalFile()));
        myZipFile.set(zip);
      }
      catch (IOException e) {
        return null;
      }
    }

    return zip;
  }

  private File getOriginalFile() {
    return new File(myBasePath);
  }

  @Nullable
  private ZipEntry convertToEntry(VirtualFile file) {
    String path = getRelativePath(file);
    final ZipFile zip = getZip();
    return zip != null ? zip.getEntry(path) : null;
  }

  public long getLength(final VirtualFile file) {
    synchronized (lock) {
      final ZipEntry entry = convertToEntry(file);
      return entry != null ? entry.getSize() : 0;
    }
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws IOException {
    return new ByteArrayInputStream(contentsToByteArray(file));
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    synchronized (lock) {
      final ZipEntry entry = convertToEntry(file);
      if (entry == null) {
        return new byte[0];
      }

      final ZipFile zip = getZip();
      assert zip != null : file;

      final InputStream stream = zip.getInputStream(entry);
      assert stream != null : file;

      try {
        return FileUtil.loadBytes(stream, (int)entry.getSize());
      }
      finally {
        stream.close();
      }
    }
  }

  public long getTimeStamp(final VirtualFile file) {
    if (file.getParent() == null) return -1L; // Optimization
    synchronized (lock) {
      final ZipEntry entry = convertToEntry(file);
      return entry != null ? entry.getTime() : -1L;
    }
  }

  public boolean isDirectory(final VirtualFile file) {
    if (file.getParent() == null) return true; // Optimization

    synchronized (lock) {
      String path = getRelativePath(file);
      final EntryInfo info = getEntriesMap().get(path);
      return info == null || info.isDirectory;
    }
  }

  private Map<String, EntryInfo> getEntriesMap() {
    return initEntries();
  }

  public boolean isWritable(final VirtualFile file) {
    return false;
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) {
      // Optimization. Do not build entries if asked for jar root existence.
      return myZipFile.get() != null || getOriginalFile().exists();
    }

    return getEntryInfo(fileOrDirectory) != null;
  }

  private static void throwReadOnly() throws IOException {
    throw new IOException("Jar file system is read-only");
  }

  @NotNull
  @SuppressWarnings({"ConstantConditions"})
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    throwReadOnly();
    return null; // Unreachable
  }

  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile copyFile(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String copyName) throws IOException {
    throwReadOnly();
    return null;
  }

  public void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException {
    throwReadOnly();
  }

  public void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException {
    throwReadOnly();
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    throwReadOnly();
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    throwReadOnly();
  }

  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    throwReadOnly();
    return null;
  }

  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    throwReadOnly();
    return null;
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    throwReadOnly();
  }
}
