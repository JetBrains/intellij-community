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
package com.intellij.openapi.util.io;

import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.BitUtil.notSet;

/**
 * @version 11.1
 */
public class FileSystemUtil {
  private static final String FORCE_USE_NIO2_KEY = "idea.io.use.nio2";
  private static final String COARSE_TIMESTAMP = "idea.io.coarse.ts";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileSystemUtil");

  private interface Mediator {
    @Nullable
    FileAttributes getAttributes(@NotNull String path) throws Exception;

    @Nullable
    String resolveSymLink(@NotNull String path) throws Exception;

    void setPermissions(@NotNull String path, int permissions) throws Exception;

    @NotNull
    String getName();
  }

  @NotNull
  private static Mediator ourMediator = getMediator();

  private static Mediator getMediator() {
    Throwable error = null;
    final boolean forceUseNio2 = SystemProperties.getBooleanProperty(FORCE_USE_NIO2_KEY, false);

    if (!forceUseNio2) {
      if (SystemInfo.isWindows && IdeaWin32.isAvailable()) {
        try {
          return check(new IdeaWin32MediatorImpl());
        }
        catch (Throwable t) {
          error = t;
        }
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris || SystemInfo.isFreeBSD) {
        try {
          return check(new JnaUnixMediatorImpl());
        }
        catch (Throwable t) {
          error = t;
        }
      }
    }

    if (SystemInfo.isJavaVersionAtLeast("1.7") && !"1.7.0-ea".equals(SystemInfo.JAVA_VERSION)) {
      try {
        return check(new Nio2MediatorImpl());
      }
      catch (Throwable t) {
        error = t;
      }
    }

    final String message =
      "Failed to load filesystem access layer (" + SystemInfo.OS_NAME + ", " + SystemInfo.JAVA_VERSION + ", " + forceUseNio2 + ")";
    LOG.error(message, error);

    return new StandardMediatorImpl();
  }

  private static Mediator check(final Mediator mediator) throws Exception {
    final String quickTestPath = SystemInfo.isWindows ? "C:\\" :  "/";
    mediator.getAttributes(quickTestPath);
    return mediator;
  }

  @TestOnly
  static void resetMediator() {
    ourMediator = getMediator();
  }

  @TestOnly
  static String getMediatorName() {
    return ourMediator.getName();
  }

  private FileSystemUtil() { }

  @Nullable
  public static FileAttributes getAttributes(@NotNull String path) {
    try {
      return ourMediator.getAttributes(path);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  @Nullable
  public static FileAttributes getAttributes(@NotNull File file) {
    return getAttributes(file.getPath());
  }

  public static long lastModified(@NotNull File file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : 0;
  }

  /**
   * Checks if a last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull String path) {
    if (SystemInfo.areSymLinksSupported) {
      final FileAttributes attributes = getAttributes(path);
      return attributes != null && attributes.isSymLink();
    }
    return false;
  }

  /**
   * Checks if a last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull File file) {
    return isSymLink(file.getAbsolutePath());
  }

  @Nullable
  public static String resolveSymLink(@NotNull String path) {
    try {
      final String realPath = ourMediator.resolveSymLink(path);
      if (realPath != null && new File(realPath).exists()) {
        return realPath;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  @Nullable
  public static String resolveSymLink(@NotNull File file) {
    return resolveSymLink(file.getAbsolutePath());
  }

  public static int getPermissions(@NotNull String path) {
    final FileAttributes attributes = getAttributes(path);
    return attributes != null ? attributes.permissions : -1;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static int getPermissions(@NotNull File file) {
    return getPermissions(file.getAbsolutePath());
  }

  public static void setPermissions(@NotNull String path, int permissions) {
    if (SystemInfo.isUnix) {
      try {
        ourMediator.setPermissions(path, permissions);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  public static void setPermissions(@NotNull File file, int permissions) {
    setPermissions(file.getAbsolutePath(), permissions);
  }


  // todo[r.sh] remove reflection after migration to JDK 7
  @SuppressWarnings("OctalInteger")
  private static class Nio2MediatorImpl implements Mediator {
    private final Object myDefaultFileSystem;
    private final Method myGetPath;
    private final Method myIsSymbolicLink;
    private final Object myLinkOptions;
    private final Object myNoFollowLinkOptions;
    private final Method myReadAttributes;
    private final Method mySetAttribute;
    private final Method myToMillis;
    private final String mySchema;

    private Nio2MediatorImpl() throws Exception {
      if (Patches.USE_REFLECTION_TO_ACCESS_JDK7) {
      myDefaultFileSystem = Class.forName("java.nio.file.FileSystems").getMethod("getDefault").invoke(null);

      myGetPath = Class.forName("java.nio.file.FileSystem").getMethod("getPath", String.class, String[].class);
      myGetPath.setAccessible(true);

      final Class<?> pathClass = Class.forName("java.nio.file.Path");
      final Class<?> filesClass = Class.forName("java.nio.file.Files");
      myIsSymbolicLink = filesClass.getMethod("isSymbolicLink", pathClass);
      myIsSymbolicLink.setAccessible(true);

      final Class<?> linkOptClass = Class.forName("java.nio.file.LinkOption");
      myLinkOptions = Array.newInstance(linkOptClass, 0);
      myNoFollowLinkOptions = Array.newInstance(linkOptClass, 1);
      Array.set(myNoFollowLinkOptions, 0, linkOptClass.getField("NOFOLLOW_LINKS").get(null));

      final Class<?> linkOptArrClass = myLinkOptions.getClass();
      myReadAttributes = filesClass.getMethod("readAttributes", pathClass, String.class, linkOptArrClass);
      myReadAttributes.setAccessible(true);
      mySetAttribute = filesClass.getMethod("setAttribute", pathClass, String.class, Object.class, linkOptArrClass);
      mySetAttribute.setAccessible(true);

      final Class<?> fileTimeClass = Class.forName("java.nio.file.attribute.FileTime");
      myToMillis = fileTimeClass.getMethod("toMillis");
      myToMillis.setAccessible(true);

      mySchema = SystemInfo.isWindows ? "dos:*" : "posix:*";
      }
    }

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      try {
        final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);

        Map attributes = (Map)myReadAttributes.invoke(null, pathObj, mySchema, myNoFollowLinkOptions);
        final boolean isSymbolicLink = (Boolean)attributes.get("isSymbolicLink");
        if (isSymbolicLink) {
          try {
            attributes = (Map)myReadAttributes.invoke(null, pathObj, mySchema, myLinkOptions);
          }
          catch (InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause != null && "java.nio.file.NoSuchFileException".equals(cause.getClass().getName())) {
              return FileAttributes.BROKEN_SYMLINK;
            }
          }
        }

        final boolean isDirectory = (Boolean)attributes.get("isDirectory");
        final boolean isOther = (Boolean)attributes.get("isOther");
        final long size = (Long)attributes.get("size");
        final long lastModified = (Long)myToMillis.invoke(attributes.get("lastModifiedTime"));
        if (SystemInfo.isWindows) {
          final boolean isHidden = (Boolean)attributes.get("hidden");
          final boolean isWritable = !(Boolean)attributes.get("readonly");
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, isHidden, size, lastModified, isWritable);
        }
        else {
          final int permissions = decodePermissions(attributes.get("permissions"));
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, size, lastModified, permissions);
        }
      }
      catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause != null && ("java.nio.file.NoSuchFileException".equals(cause.getClass().getName()) ||
                              "java.nio.file.InvalidPathException".equals(cause.getClass().getName()))) {
          LOG.debug(cause);
          return null;
        }
        throw e;
      }
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      if (!new File(path).exists()) return null;
      assert Patches.USE_REFLECTION_TO_ACCESS_JDK7;

      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Method toRealPath = pathObj.getClass().getMethod("toRealPath", myLinkOptions.getClass());
      toRealPath.setAccessible(true);
      return toRealPath.invoke(pathObj, myLinkOptions).toString();

    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Object attribute = encodePermissions(permissions);
      if (attribute != null) {
        mySetAttribute.invoke(null, pathObj, "posix:permissions", attribute, myLinkOptions);
      }
    }

    @NotNull
    @Override
    public String getName() {
      return "NIO2";
    }

    private static final Map<String, Integer> ATTRIBUTES_MAP;
    static {
      ATTRIBUTES_MAP = new HashMap<String, Integer>();
      ATTRIBUTES_MAP.put("OWNER_READ", FileAttributes.OWNER_READ);
      ATTRIBUTES_MAP.put("OWNER_WRITE", FileAttributes.OWNER_WRITE);
      ATTRIBUTES_MAP.put("OWNER_EXECUTE", FileAttributes.OWNER_EXECUTE);
      ATTRIBUTES_MAP.put("GROUP_READ", FileAttributes.GROUP_READ);
      ATTRIBUTES_MAP.put("GROUP_WRITE", FileAttributes.GROUP_WRITE);
      ATTRIBUTES_MAP.put("GROUP_EXECUTE", FileAttributes.GROUP_EXECUTE);
      ATTRIBUTES_MAP.put("OTHERS_READ", FileAttributes.OTHERS_READ);
      ATTRIBUTES_MAP.put("OTHERS_WRITE", FileAttributes.OTHERS_WRITE);
      ATTRIBUTES_MAP.put("OTHERS_EXECUTE", FileAttributes.OTHERS_EXECUTE);
    }

    @FileAttributes.Permissions
    private static int decodePermissions(final Object o) {
      if (!(o instanceof Collection)) return -1;

      int value = 0;
      for (Object attr : (Collection)o) {
        final Integer bit = ATTRIBUTES_MAP.get(attr.toString());
        if (bit != null) value |= bit;
      }
      //noinspection MagicConstant
      return value;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Object encodePermissions(final int value) {
      try {
        final Class aClass = Class.forName("java.nio.file.attribute.PosixFilePermission");
        final Set values = new HashSet();
        for (Map.Entry<String, Integer> entry : ATTRIBUTES_MAP.entrySet()) {
          if ((value & entry.getValue()) > 0) {
            final String name = entry.getKey();
            values.add(Enum.valueOf(aClass, name));
          }
        }
        return values;
      }
      catch (Exception ignore) { }

      return null;
    }
  }


  private static class IdeaWin32MediatorImpl implements Mediator {
    private IdeaWin32 myInstance = IdeaWin32.getInstance();

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      final FileInfo fileInfo = myInstance.getInfo(path);
      return fileInfo != null ? fileInfo.toFileAttributes() : null;
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return myInstance.resolveSymLink(path);
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
    }

    @NotNull
    @Override
    public String getName() {
      return "IdeaWin32";
    }
  }


  // thanks to SVNKit for the idea
  private static class JnaUnixMediatorImpl implements Mediator {
    @SuppressWarnings({"OctalInteger", "SpellCheckingInspection"})
    private interface LibC extends Library {
      // from stat(2)
      int S_MASK = 0177777;
      int S_IFLNK = 0120000;  // symbolic link
      int S_IFREG = 0100000;  // regular file
      int S_IFDIR = 0040000;  // directory
      int PERM_MASK = 0777;

      int lstat(String path, Pointer stat);
      int stat(String path, Pointer stat);
      int __lxstat64(int ver, String path, Pointer stat);
      int __xstat64(int ver, String path, Pointer stat);
      int chmod(String path, int mode);
    }

    private final LibC myLibC;
    private final int myModeOffset;
    private final int mySizeOffset;
    private final int myTimeOffset;
    private final boolean myCoarseTs = SystemProperties.getBooleanProperty(COARSE_TIMESTAMP, false);

    private JnaUnixMediatorImpl() throws Exception {
      myModeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 16 : 24) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? 8 :
                     SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 20 : 16) :
                     -1;
      mySizeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 44 : 48) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? (SystemInfo.is32Bit ? 48 : 72) :
                     SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 48 : 40) :
                     -1;
      myTimeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 72 : 88) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? (SystemInfo.is32Bit ? 32 : 40) :
                     SystemInfo.isSolaris ? 64 :
                     -1;
      if (myModeOffset < 0) throw new IllegalStateException("Unsupported OS: " + SystemInfo.OS_NAME);

      myLibC = (LibC)Native.loadLibrary("c", LibC.class);
    }

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      Memory buffer = new Memory(256);
      int res = SystemInfo.isLinux ? myLibC.__lxstat64(0, path, buffer) : myLibC.lstat(path, buffer);
      if (res != 0) return null;

      int mode = (SystemInfo.isLinux ? buffer.getInt(myModeOffset) : buffer.getShort(myModeOffset)) & LibC.S_MASK;
      boolean isSymlink = (mode & LibC.S_IFLNK) == LibC.S_IFLNK;
      if (isSymlink) {
        res = SystemInfo.isLinux ? myLibC.__xstat64(0, path, buffer) : myLibC.stat(path, buffer);
        if (res != 0) {
          return FileAttributes.BROKEN_SYMLINK;
        }
        mode = (SystemInfo.isLinux ? buffer.getInt(myModeOffset) : buffer.getShort(myModeOffset)) & LibC.S_MASK;
      }

      boolean isDirectory = (mode & LibC.S_IFDIR) == LibC.S_IFDIR;
      boolean isSpecial = !isDirectory && (mode & LibC.S_IFREG) == 0;
      long size = buffer.getLong(mySizeOffset);
      long mTime1 = SystemInfo.is32Bit ? buffer.getInt(myTimeOffset) : buffer.getLong(myTimeOffset);
      long mTime2 = myCoarseTs ? 0 : SystemInfo.is32Bit ? buffer.getInt(myTimeOffset + 4) : buffer.getLong(myTimeOffset + 8);
      long mTime = mTime1 * 1000 + mTime2 / 1000000;
      @FileAttributes.Permissions int permissions = mode & LibC.PERM_MASK;
      return new FileAttributes(isDirectory, isSpecial, isSymlink, size, mTime, permissions);
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      try {
        return new File(path).getCanonicalPath();
      }
      catch (IOException e) {
        final String message = e.getMessage();
        if (message != null && message.toLowerCase().contains("too many levels of symbolic links")) {
          LOG.debug(e);
          return null;
        }
        throw new IOException("Cannot resolve '" + path + "'", e);
      }
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      myLibC.chmod(path, permissions & LibC.PERM_MASK);
    }

    @NotNull
    @Override
    public String getName() {
      return "JnaUnix";
    }
  }


  private static class StandardMediatorImpl implements Mediator {
    // from java.io.FileSystem
    private static final int BA_REGULAR   = 0x02;
    private static final int BA_DIRECTORY = 0x04;
    private static final int BA_HIDDEN    = 0x08;

    private final Object myFileSystem;
    private final Method myGetBooleanAttributes;

    private StandardMediatorImpl() {
      Object fileSystem;
      Method getBooleanAttributes;
      try {
        final Class<?> fsClass = Class.forName("java.io.FileSystem");
        final Method getFileSystem = fsClass.getMethod("getFileSystem");
        getFileSystem.setAccessible(true);
        fileSystem = getFileSystem.invoke(null);
        getBooleanAttributes = fsClass.getDeclaredMethod("getBooleanAttributes", File.class);
        getBooleanAttributes.setAccessible(true);
      }
      catch (Throwable t) {
        fileSystem = null;
        getBooleanAttributes = null;
      }
      myFileSystem = fileSystem;
      myGetBooleanAttributes = getBooleanAttributes;
    }

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      final File file = new File(path);
      if (myFileSystem != null) {
        final int flags = (Integer)myGetBooleanAttributes.invoke(myFileSystem, file);
        if (flags != 0) {
          final boolean isDirectory = isSet(flags, BA_DIRECTORY);
          final boolean isSpecial = notSet(flags, BA_REGULAR | BA_DIRECTORY);
          final boolean isHidden = isSet(flags, BA_HIDDEN);
          return new FileAttributes(isDirectory, isSpecial, false, isHidden, file.length(), file.lastModified(), file.canWrite());
        }
      }
      else {
        if (file.exists()) {
          final boolean isDirectory = file.isDirectory();
          final boolean isSpecial = !isDirectory && !file.isFile();
          final boolean isHidden = file.isHidden();
          return new FileAttributes(isDirectory, isSpecial, false, isHidden, file.length(), file.lastModified(), file.canWrite());
        }
      }

      return null;
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
    }

    @NotNull
    @Override
    public String getName() {
      return "fallback";
    }
  }
}
