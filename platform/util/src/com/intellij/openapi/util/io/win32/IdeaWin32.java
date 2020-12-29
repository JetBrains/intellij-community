// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io.win32;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.loader.NativeLibraryLoader;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.CRC32;

/**
 * Do not use this class directly.
 *
 * @author Dmitry Avdeev
 */
public final class IdeaWin32 {
  private static final Logger LOG = Logger.getInstance(IdeaWin32.class);
  private static final boolean TRACE_ENABLED = LOG.isTraceEnabled();

  private static final IdeaWin32 ourInstance;

  static {
    IdeaWin32 instance = null;
    if (SystemInfoRt.isWindows && Boolean.parseBoolean(System.getProperty("idea.use.native.fs.for.win", "true"))) {
      try {
        if (!loadBundledLibrary()) {
          NativeLibraryLoader.loadPlatformLibrary("IdeaWin32");
        }
        instance = new IdeaWin32();
        LOG.info("Native filesystem for Windows is operational");
      }
      catch (Throwable t) {
        LOG.warn("Failed to initialize native filesystem for Windows", t);
      }
    }
    ourInstance = instance;
  }

  private static boolean loadBundledLibrary() throws IOException {
    String name = CpuArch.isIntel64() ? "IdeaWin64" : "IdeaWin32";
    URL bundled = IdeaWin32.class.getResource(name + ".dll");
    if (bundled == null) {
      return false;
    }

    byte[] content = FileUtilRt.loadBytes(bundled.openStream());
    CRC32 crc32 = new CRC32();
    crc32.update(content, 0, content.length);
    long hash = Math.abs(crc32.getValue());
    Path file = Paths.get(FileUtilRt.getTempDirectory(), name + '.' + hash + ".dll");
    if (!Files.exists(file)) {
      Files.createDirectories(file.getParent());
      Files.write(file, content);
    }
    System.load(file.toString());
    return true;
  }

  public static boolean isAvailable() {
    return ourInstance != null;
  }

  public static @NotNull IdeaWin32 getInstance() {
    if (!isAvailable()) {
      throw new IllegalStateException("Native filesystem for Windows is not loaded");
    }
    return ourInstance;
  }

  private IdeaWin32() {
    initIDs();
  }

  private static native void initIDs();

  public @Nullable FileInfo getInfo(@NotNull String path) {
    path = path.replace('/', '\\');
    if (TRACE_ENABLED) {
      LOG.trace("getInfo(" + path + ")");
      long t = System.nanoTime();
      FileInfo result = getInfo0(path);
      t = (System.nanoTime() - t) / 1000;
      LOG.trace("  " + t + " mks");
      return result;
    }
    else {
      return getInfo0(path);
    }
  }

  public @Nullable String resolveSymLink(@NotNull String path) {
    path = path.replace('/', '\\');
    if (TRACE_ENABLED) {
      LOG.trace("resolveSymLink(" + path + ")");
      long t = System.nanoTime();
      String result = resolveSymLink0(path);
      t = (System.nanoTime() - t) / 1000;
      LOG.trace("  " + t + " mks");
      return result;
    }
    else {
      return resolveSymLink0(path);
    }
  }

  public FileInfo @Nullable [] listChildren(@NotNull String path) {
    path = path.replace('/', '\\');
    if (TRACE_ENABLED) {
      LOG.trace("list(" + path + ")");
      long t = System.nanoTime();
      FileInfo[] children = listChildren0(path);
      t = (System.nanoTime() - t) / 1000;
      LOG.trace("  " + (children == null ? -1 : children.length) + " children, " + t + " mks");
      return children;
    }
    else {
      return listChildren0(path);
    }
  }

  private native FileInfo getInfo0(String path);

  private native String resolveSymLink0(String path);

  private native FileInfo[] listChildren0(String path);
}
