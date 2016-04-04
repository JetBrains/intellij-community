/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util.io.win32;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.zip.CRC32;

/**
 * Do not use this class directly.
 *
 * @author Dmitry Avdeev
 * @since 12.0
 */
public class IdeaWin32 {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.win32.IdeaWin32");
  private static final boolean TRACE_ENABLED = LOG.isTraceEnabled();

  private static final IdeaWin32 ourInstance;

  static {
    IdeaWin32 instance = null;
    if (SystemInfo.isWin2kOrNewer && SystemProperties.getBooleanProperty("idea.use.native.fs.for.win", true)) {
      try {
        if (!loadBundledLibrary()) {
          UrlClassLoader.loadPlatformLibrary("IdeaWin32");
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
    String name = SystemInfo.is64Bit ? "IdeaWin64" : "IdeaWin32";
    URL bundled = IdeaWin32.class.getResource(name + ".dll");
    if (bundled == null) return false;
    byte[] content = FileUtil.loadBytes(bundled.openStream());
    CRC32 crc32 = new CRC32();
    crc32.update(content);
    long hash = Math.abs(crc32.getValue());
    File file = new File(FileUtil.getTempDirectory(), name + '.' + hash + ".dll");
    if (!file.exists()) FileUtil.writeToFile(file, content);
    System.load(file.getPath());
    return true;
  }

  public static boolean isAvailable() {
    return ourInstance != null;
  }

  @NotNull
  public static IdeaWin32 getInstance() {
    if (!isAvailable()) {
      throw new IllegalStateException("Native filesystem for Windows is not loaded");
    }
    return ourInstance;
  }

  private IdeaWin32() {
    initIDs();
  }

  private static native void initIDs();

  @Nullable
  public FileInfo getInfo(@NotNull String path) {
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

  @Nullable
  public String resolveSymLink(@NotNull String path) {
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

  @Nullable
  public FileInfo[] listChildren(@NotNull String path) {
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
