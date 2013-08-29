/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author max
 */
public class JarHandler extends JarHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarHandler");

  @NonNls private static final String JARS_FOLDER = "jars";
  private static final int FS_TIME_RESOLUTION = 2000;

  private final JarFileSystemImpl myFileSystem;

  public JarHandler(@NotNull JarFileSystemImpl fileSystem, @NotNull String path) {
    super(path);
    myFileSystem = fileSystem;
  }

  public void refreshLocalFileForJar() {
    NewVirtualFile localJarFile = (NewVirtualFile)LocalFileSystem.getInstance().refreshAndFindFileByPath(myBasePath);
    if (localJarFile != null) {
      localJarFile.markDirty();
    }
  }

  @Nullable
  public VirtualFile markDirty() {
    clear();

    final VirtualFile root = JarFileSystem.getInstance().findFileByPath(myBasePath + JarFileSystem.JAR_SEPARATOR);
    if (root instanceof NewVirtualFile) {
      ((NewVirtualFile)root).markDirty();
    }
    return root;
  }

  @Override
  public File getMirrorFile(@NotNull File originalFile) {
    if (!myFileSystem.isMakeCopyOfJar(originalFile)) return originalFile;

    final FileAttributes originalAttributes = FileSystemUtil.getAttributes(originalFile);
    if (originalAttributes == null) return originalFile;

    final String folderPath = getJarsDir();
    if (!new File(folderPath).exists() && !new File(folderPath).mkdirs()) {
      return originalFile;
    }

    if (FSRecords.weHaveContentHashes) {
      return getMirrorWithContentHash(originalFile, originalAttributes);
    }

    final String mirrorName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    final File mirrorFile = new File(folderPath, mirrorName);
    final FileAttributes mirrorAttributes = FileSystemUtil.getAttributes(mirrorFile);

    if (mirrorAttributes == null ||
        originalAttributes.length != mirrorAttributes.length ||
        Math.abs(originalAttributes.lastModified - mirrorAttributes.lastModified) > FS_TIME_RESOLUTION) {
      return copyToMirror(originalFile, mirrorFile);
    }

    return mirrorFile;
  }

  private File getMirrorWithContentHash(File originalFile,
                                        FileAttributes originalAttributes) {
    File mirrorFile = null;
    String jarDir = getJarsDir();

    try {
      String path = originalFile.getPath();
      CacheLibraryInfo info = CacheLibraryInfo.ourCachedLibraryInfo.get(path);
      FileAttributes mirrorFileAttributes;

      if (info != null) {
        if (originalAttributes.length == info.myFileLength &&
            Math.abs(originalAttributes.lastModified - info.myModificationTime) <= FS_TIME_RESOLUTION
           ) {
          mirrorFile = new File(new File(jarDir), info.mySnapshotPath);
          mirrorFileAttributes = FileSystemUtil.getAttributes(mirrorFile);
          if (mirrorFileAttributes != null &&
              mirrorFileAttributes.length == originalAttributes.length &&
              // no abs, reuse if cached file is older, mirrors can be from different projects with different modification times
              (mirrorFileAttributes.lastModified - originalAttributes.lastModified) <= FS_TIME_RESOLUTION
            ) {
            return mirrorFile;
          }
        }
      }

      DataOutputStream os = null;
      FileInputStream is = null;

      MessageDigest sha1 = null;
      File tempJarFile = null;

      try {
        tempJarFile = FileUtil.createTempFile(new File(jarDir), originalFile.getName(), "", true, false);
        os = new DataOutputStream(new FileOutputStream(tempJarFile));
        is = new FileInputStream(originalFile);
        byte[] buffer = new byte[20 * 1024];

        sha1 = MessageDigest.getInstance("SHA1");
        sha1.update(String.valueOf(originalAttributes.length).getBytes(Charset.defaultCharset()));
        sha1.update("\0".getBytes(Charset.defaultCharset()));

        while(true) {
          int read = is.read(buffer);
          if (read == -1) break;
          sha1.update(buffer, 0, read);
          os.write(buffer, 0, read);
        }
      }
      catch (IOException ex) {
        File target = mirrorFile != null ? mirrorFile : tempJarFile != null ? tempJarFile : new File(jarDir);
        reportIOErrorWithJars(originalFile, target, ex);
        return originalFile;
      }
      catch (NoSuchAlgorithmException ex) {
        assert false;
        return originalFile; // should never happen for sha1
      }
      finally {
        if (os != null) try {os.close();} catch (IOException ignored) {}
        if (is != null) try {is.close();} catch (IOException ignored) {}
      }

      byte[] digest = sha1.digest();
      mirrorFile = new File(new File(jarDir), getSnapshotName(originalFile.getName(), digest));
      mirrorFileAttributes = FileSystemUtil.getAttributes(mirrorFile);

      if (mirrorFileAttributes == null ||
          originalAttributes.length != mirrorFileAttributes.length ||
          mirrorFileAttributes.lastModified - originalAttributes.lastModified > FS_TIME_RESOLUTION // no abs, avoid leaving lately modified mirrors
        ) {
        try {
          if (mirrorFileAttributes != null) {
            FileUtil.delete(mirrorFile);
          }
          FileUtil.rename(tempJarFile, mirrorFile);
          mirrorFile.setLastModified(originalAttributes.lastModified);
        } catch (IOException ex) {
          reportIOErrorWithJars(originalFile, mirrorFile, ex);
          return originalFile;
        }
      } else {
        FileUtil.delete(tempJarFile);
      }

      info = new CacheLibraryInfo(mirrorFile.getName(), originalAttributes.lastModified, originalAttributes.length);
      CacheLibraryInfo.ourCachedLibraryInfo.put(path, info);
      CacheLibraryInfo.ourCachedLibraryInfo.force();
      return mirrorFile;
    } catch (IOException ex) {
      reportIOErrorWithJars(originalFile, mirrorFile != null ? mirrorFile: new File(jarDir, originalFile.getName()), ex);
      return originalFile;
    }
  }

  private String getSnapshotName(String name, byte[] digest) {
    StringBuilder builder = new StringBuilder(name.length() + 1 + 2 * digest.length);
    builder.append(name).append('.');
    for(byte b:digest) {
      builder.append(Character.forDigit((b & 0xF0) >> 4, 16));
      builder.append(Character.forDigit(b & 0xF, 16));
    }

    return builder.toString();
  }

  @NotNull
  private static String getJarsDir() {
    String dir = System.getProperty("jars_dir");
    return dir == null ? PathManager.getSystemPath() + File.separatorChar + JARS_FOLDER : dir;
  }

  @NotNull
  private File copyToMirror(@NotNull File original, @NotNull File mirror) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(VfsBundle.message("jar.copy.progress", original.getPath()));
      progress.setFraction(0);
    }

    try {
      FileUtil.copy(original, mirror);
    }
    catch (final IOException e) {
      reportIOErrorWithJars(original, mirror, e);
      return original;
    }

    if (progress != null) {
      progress.popState();
    }

    return mirror;
  }

  private static class CacheLibraryInfo {
    private final String mySnapshotPath;
    private final long myModificationTime;
    private final long myFileLength;

    private static final PersistentHashMap<String, CacheLibraryInfo> ourCachedLibraryInfo;

    static {
      File file = new File(new File(getJarsDir()), "snapshots_info");
      PersistentHashMap<String, CacheLibraryInfo> info = null;
      for (int i = 0; i < 2; ++i) {
        try {
          info = new PersistentHashMap<String, CacheLibraryInfo>(
            file, new EnumeratorStringDescriptor(), new DataExternalizer<CacheLibraryInfo>() {
            private final byte[] myBuffer = IOUtil.allocReadWriteUTFBuffer();

            @Override
            public void save(DataOutput out, CacheLibraryInfo value) throws IOException {
              IOUtil.writeUTFFast(myBuffer, out, value.mySnapshotPath);
              out.writeLong(value.myModificationTime);
              out.writeLong(value.myFileLength);
            }

            @Override
            public CacheLibraryInfo read(DataInput in) throws IOException {
              return new CacheLibraryInfo(IOUtil.readUTFFast(myBuffer, in), in.readLong(), in.readLong());
            }
          }
          );
          break;
        } catch (IOException ex) {
          PersistentHashMap.deleteFilesStartingWith(file);
        }
      }
      assert info != null;
      ourCachedLibraryInfo = info;
    }

    private CacheLibraryInfo(@NotNull String path, long time, long length) {
      mySnapshotPath = path;
      myModificationTime = time;
      myFileLength = length;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CacheLibraryInfo info = (CacheLibraryInfo)o;

      if (myFileLength != info.myFileLength) return false;
      if (myModificationTime != info.myModificationTime) return false;
      if (!mySnapshotPath.equals(info.mySnapshotPath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = mySnapshotPath.hashCode();
      result = 31 * result + (int)(myModificationTime ^ (myModificationTime >>> 32));
      result = 31 * result + (int)(myFileLength ^ (myFileLength >>> 32));
      return result;
    }
  }

  private static final NotNullLazyValue<NotificationGroup> ERROR_COPY_NOTIFICATION = new NotNullLazyValue<NotificationGroup>() {
    @NotNull
    @Override
    protected NotificationGroup compute() {
      return NotificationGroup.balloonGroup(VfsBundle.message("jar.copy.error.title"));
    }
  };

  private void reportIOErrorWithJars(File original, File target, IOException e) {
    LOG.warn(e);

    String path = original.getPath();
    myFileSystem.setNoCopyJarForPath(path);

    String message = VfsBundle.message("jar.copy.error.message", path, target.getPath(), e.getMessage());
    ERROR_COPY_NOTIFICATION.getValue().createNotification(message, NotificationType.ERROR).notify(null);
  }
}
