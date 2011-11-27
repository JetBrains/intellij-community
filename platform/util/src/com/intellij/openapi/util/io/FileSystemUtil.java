/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

// todo[r.sh] use NIO2 API after migration to JDK 7
public class FileSystemUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileSystemUtil");

  @Nullable
  private static final Mediator ourMediator;

  static {
    Mediator mediator = null;
    if (SystemInfo.areSymLinksSupported) {
      if (SystemInfo.isJavaVersionAtLeast("1.7")) {
        try {
          mediator = new Jdk7MediatorImpl();
          mediator.isSymLink("/");  // quick test
        }
        catch (Throwable t) {
          if ("1.7.0-ea".equals(SystemInfo.JAVA_VERSION)) {
            LOG.warn(t);
          }
          else {
            LOG.error(t);
          }
          mediator = null;
        }
      }
      if (mediator == null) {
        if (SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris || SystemInfo.isFreeBSD) {
          try {
            mediator = new JnaUnixMediatorImpl();
            mediator.isSymLink("/");  // quick test
          }
          catch (Throwable t) {
            LOG.error(t);
            mediator = null;
          }
        }
      }
    }
    ourMediator = mediator;
  }

  private FileSystemUtil() { }

  public static boolean isSymLink(@NotNull final File file) {
    try {
      return ourMediator != null && file.exists() && ourMediator.isSymLink(file.getAbsolutePath());
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  public static boolean isSymLink(@NotNull final String path) {
    return isSymLink(new File(path));
  }

  @Nullable
  public static String resolveSymLink(@NotNull final File file) {
    if (file.exists() && ourMediator != null) {
      try {
        final String realPath = ourMediator.resolveSymLink(file.getAbsolutePath());
        if (realPath != null && new File(realPath).exists()) {
          return realPath;
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return null;
  }

  @Nullable
  public static String resolveSymLink(@NotNull final String path) {
    return resolveSymLink(new File(path));
  }

  public static int getPermissions(@NotNull final String path) {
    return getPermissions(new File(path));
  }

  public static int getPermissions(@NotNull final File file) {
    if (file.exists() && SystemInfo.isUnix && ourMediator != null) {
      try {
        return ourMediator.getPermissions(file.getAbsolutePath());
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return -1;
  }

  public static void setPermissions(@NotNull final String path, final int permissions) {
    setPermissions(new File(path), permissions);
  }

  public static void setPermissions(@NotNull final File file, final int permissions) {
    if (file.exists() && SystemInfo.isUnix && ourMediator != null) {
      try {
        ourMediator.setPermissions(file.getAbsolutePath(), permissions);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  private interface Mediator {
    boolean isSymLink(@NotNull String path) throws Exception;

    @Nullable
    String resolveSymLink(@NotNull String path) throws Exception;

    int getPermissions(@NotNull String path) throws Exception;

    void setPermissions(@NotNull String path, int permissions) throws Exception;
  }

  @SuppressWarnings("OctalInteger")
  private static class Jdk7MediatorImpl implements Mediator {
    private final Object myDefaultFileSystem;
    private final Method myGetPath;
    private final Method myIsSymbolicLink;
    private final Object myLinkOptions;
    private final Method myGetAttribute;
    private final Method mySetAttribute;

    private Jdk7MediatorImpl() throws Exception {
      myDefaultFileSystem = Class.forName("java.nio.file.FileSystems").getMethod("getDefault").invoke(null);
      myGetPath = Class.forName("java.nio.file.FileSystem").getMethod("getPath", String.class, String[].class);
      myGetPath.setAccessible(true);
      final Class<?> pathClass = Class.forName("java.nio.file.Path");
      myIsSymbolicLink = Class.forName("java.nio.file.Files").getMethod("isSymbolicLink", pathClass);
      myIsSymbolicLink.setAccessible(true);
      myLinkOptions = Array.newInstance(Class.forName("java.nio.file.LinkOption"), 0);
      final Class<?> linkOptClass = myLinkOptions.getClass();
      myGetAttribute = Class.forName("java.nio.file.Files").getMethod("getAttribute", pathClass, String.class, linkOptClass);
      mySetAttribute = Class.forName("java.nio.file.Files").getMethod("setAttribute", pathClass, String.class, Object.class, linkOptClass);
    }

    @Override
    public boolean isSymLink(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      return (Boolean)myIsSymbolicLink.invoke(null, pathObj);
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Method toRealPath = pathObj.getClass().getMethod("toRealPath", myLinkOptions.getClass());
      toRealPath.setAccessible(true);
      return toRealPath.invoke(pathObj, myLinkOptions).toString();
    }

    @Override
    public int getPermissions(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Object attribute = myGetAttribute.invoke(null, pathObj, POSIX_PERMISSIONS_ATTR, myLinkOptions);
      return decodePermissions(attribute);
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Object attribute = encodePermissions(permissions);
      if (attribute != null) {
        mySetAttribute.invoke(null, pathObj, POSIX_PERMISSIONS_ATTR, attribute, myLinkOptions);
      }
    }

    private static final String POSIX_PERMISSIONS_ATTR = "posix:permissions";

    private static final Map<String, Integer> ATTRIBUTES_MAP;
    static {
      ATTRIBUTES_MAP = new HashMap<String, Integer>();
      ATTRIBUTES_MAP.put("OWNER_READ", 0400);
      ATTRIBUTES_MAP.put("OWNER_WRITE", 0200);
      ATTRIBUTES_MAP.put("OWNER_EXECUTE", 0100);
      ATTRIBUTES_MAP.put("GROUP_READ", 0040);
      ATTRIBUTES_MAP.put("GROUP_WRITE", 0020);
      ATTRIBUTES_MAP.put("GROUP_EXECUTE", 0010);
      ATTRIBUTES_MAP.put("OTHERS_READ", 0004);
      ATTRIBUTES_MAP.put("OTHERS_WRITE", 0002);
      ATTRIBUTES_MAP.put("OTHERS_EXECUTE", 0001);
    }

    private static int decodePermissions(final Object o) {
      if (!(o instanceof Collection)) return -1;

      int value = 0;
      for (Object attr : (Collection)o) {
        final Integer bit = ATTRIBUTES_MAP.get(attr.toString());
        if (bit != null) value |= bit;
      }
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

  // thanks to SVNKit for the idea
  @SuppressWarnings("OctalInteger")
  private static class JnaUnixMediatorImpl implements Mediator {
    private interface LibC extends Library {
      int S_MASK = 0177777;
      int S_IFLNK = 0120000;
      int PERM_MASK = 0777;

      int lstat(String path, Pointer stat);
      int __lxstat64(int ver, String path, Pointer stat);
      int chmod(String path, int mode);
    }

    private final LibC myLibC;
    private final Memory mySharedMem;
    private final int myOffset;

    private JnaUnixMediatorImpl() throws Exception {
      myLibC = (LibC)Native.loadLibrary("c", LibC.class);
      mySharedMem = new Memory(512);
      myOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 16 : 24) :
                 SystemInfo.isMac | SystemInfo.isFreeBSD ? 8 :
                 SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 20 : 16) :
                 -1;
      if (myOffset < 0) throw new IllegalStateException("Unsupported OS: " + SystemInfo.OS_NAME);
    }

    private synchronized int getMode(final String path) {
      mySharedMem.clear();
      final int res = SystemInfo.isLinux ? myLibC.__lxstat64(0, path, mySharedMem) : myLibC.lstat(path, mySharedMem);
      if (res == 0) {
        return (SystemInfo.isLinux ? mySharedMem.getInt(myOffset) : mySharedMem.getShort(myOffset)) & LibC.S_MASK;
      }
      else {
        LOG.debug("lstat(" + path + "): " + res);
        return -1;
      }
    }

    @Override
    public boolean isSymLink(@NotNull final String path) throws Exception {
      final int mode = getMode(path);
      return mode != -1 && (mode & LibC.S_IFLNK) == LibC.S_IFLNK;
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }

    @Override
    public int getPermissions(@NotNull final String path) throws Exception {
      final int mode = getMode(path);
      return mode != -1 ? mode & LibC.PERM_MASK : -1;
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      myLibC.chmod(path, permissions & LibC.PERM_MASK);
    }
  }
}
