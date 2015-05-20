/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author max
 */
public class JarHandler extends ZipHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarHandler");

  private static final String JARS_FOLDER = "jars";
  private static final int FS_TIME_RESOLUTION = 2000;

  private final JarFileSystemImpl myFileSystem;
  private volatile File myFileWithMirrorResolved;

  public JarHandler(@NotNull String path) {
    super(path);
    myFileSystem = (JarFileSystemImpl)JarFileSystem.getInstance();
  }

  @NotNull
  @Override
  protected File getFileToUse() {
    File fileWithMirrorResolved = myFileWithMirrorResolved;
    if (fileWithMirrorResolved == null) {
      File file = getFile();
      fileWithMirrorResolved = getMirrorFile(file);
      if (FileUtil.compareFiles(file, fileWithMirrorResolved) == 0) {
        fileWithMirrorResolved = file;
      }
      myFileWithMirrorResolved = fileWithMirrorResolved;
    }
    return fileWithMirrorResolved;
  }

  private File getMirrorFile(@NotNull File originalFile) {
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
    return mirrorDiffers(originalAttributes, mirrorAttributes, false) ? copyToMirror(originalFile, mirrorFile) : mirrorFile;
  }

  private File getMirrorWithContentHash(File originalFile, FileAttributes originalAttributes) {
    File mirrorFile = null;
    String jarDir = getJarsDir();

    try {
      String path = originalFile.getPath();
      CacheLibraryInfo info = CacheLibraryInfo.ourCachedLibraryInfo.get(path);

      if (info != null &&
          originalAttributes.length == info.myFileLength &&
          Math.abs(originalAttributes.lastModified - info.myModificationTime) <= FS_TIME_RESOLUTION) {
        mirrorFile = new File(jarDir, info.mySnapshotPath);
        if (!mirrorDiffers(originalAttributes, FileSystemUtil.getAttributes(mirrorFile), true)) {
          return mirrorFile;
        }
      }

      MessageDigest sha1 = null;
      File tempJarFile = null;

      try {
        tempJarFile = FileUtil.createTempFile(new File(jarDir), originalFile.getName(), "", true, false);

        DataOutputStream os = new DataOutputStream(new FileOutputStream(tempJarFile));
        try {
          FileInputStream is = new FileInputStream(originalFile);
          try {
            sha1 = MessageDigest.getInstance("SHA1");
            sha1.update(String.valueOf(originalAttributes.length).getBytes(Charset.defaultCharset()));
            sha1.update((byte)0);

            byte[] buffer = new byte[20 * 1024];
            while (true) {
              int read = is.read(buffer);
              if (read < 0) break;
              sha1.update(buffer, 0, read);
              os.write(buffer, 0, read);
            }
          }
          finally {
            is.close();
          }
        }
        finally {
          os.close();
        }
      }
      catch (IOException ex) {
        File target = mirrorFile != null ? mirrorFile : tempJarFile != null ? tempJarFile : new File(jarDir);
        reportIOErrorWithJars(originalFile, target, ex);
        return originalFile;
      }
      catch (NoSuchAlgorithmException ex) {
        LOG.error(ex);
        return originalFile; // should never happen for sha1
      }

      String mirrorName = getSnapshotName(originalFile.getName(), sha1.digest());
      mirrorFile = new File(jarDir, mirrorName);

      if (mirrorDiffers(originalAttributes, FileSystemUtil.getAttributes(mirrorFile), true)) {
        try {
          FileUtil.delete(mirrorFile);
          FileUtil.rename(tempJarFile, mirrorFile);
          FileUtil.setLastModified(mirrorFile, originalAttributes.lastModified);
        }
        catch (IOException ex) {
          reportIOErrorWithJars(originalFile, mirrorFile, ex);
          return originalFile;
        }
      }
      else {
        FileUtil.delete(tempJarFile);
      }

      info = new CacheLibraryInfo(mirrorFile.getName(), originalAttributes.lastModified, originalAttributes.length);
      CacheLibraryInfo.ourCachedLibraryInfo.put(path, info);
      return mirrorFile;
    }
    catch (IOException ex) {
      reportIOErrorWithJars(originalFile, mirrorFile != null ? mirrorFile : new File(jarDir, originalFile.getName()), ex);
      return originalFile;
    }
  }

  private static boolean mirrorDiffers(FileAttributes original, @Nullable FileAttributes mirror, boolean permitOlderMirror) {
    if (mirror == null || mirror.length != original.length) return true;
    long timeDiff = mirror.lastModified - original.lastModified;
    if (!permitOlderMirror) timeDiff = Math.abs(timeDiff);
    return timeDiff > FS_TIME_RESOLUTION;
  }

  private static String getSnapshotName(String name, byte[] digest) {
    StringBuilder builder = new StringBuilder(name.length() + 1 + 2 * digest.length);
    builder.append(name).append('.');
    for (byte b : digest) {
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

            @Override
            public void save(@NotNull DataOutput out, CacheLibraryInfo value) throws IOException {
              IOUtil.writeUTF(out, value.mySnapshotPath);
              out.writeLong(value.myModificationTime);
              out.writeLong(value.myFileLength);
            }

            @Override
            public CacheLibraryInfo read(@NotNull DataInput in) throws IOException {
              return new CacheLibraryInfo(IOUtil.readUTF(in), in.readLong(), in.readLong());
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
      FlushingDaemon.everyFiveSeconds(new Runnable() {
        @Override
        public void run() {
          flushCachedLibraryInfos();
        }
      });

      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        @Override
        public void run() {
          flushCachedLibraryInfos();
        }
      });
    }

    private static void flushCachedLibraryInfos() {
      if (ourCachedLibraryInfo.isDirty()) ourCachedLibraryInfo.force();
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
