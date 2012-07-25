/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Do not use this class directly.
 *
 * @author Dmitry Avdeev
 * @since 12.0
 */
public class IdeaWin32 {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.win32.Win32LocalFileSystem");

  private static final String PATH_PREFIX = "\\\\?\\";
  private static final int PREFIX_SIZE = PATH_PREFIX.length();
  private static final String PATH_SUFFIX =  "\\*.*";
  private static final int MAX_PATH = 260;

  private static final IdeaWin32 ourInstance;

  static {
    boolean available = false;
    if (SystemInfo.isWindows) {
      String libName = SystemInfo.is64Bit ? "IdeaWin64" : "IdeaWin32";
      try {
        System.load(PathManager.getHomePath() + "/community/bin/win/" + libName + ".dll");
        available = true;
      }
      catch (Throwable t0) {
        try {
          System.load(PathManager.getHomePath() + "/bin/win/" + libName + ".dll");
          available = true;
        }
        catch (Throwable t1) {
          try {
            System.loadLibrary(libName);
            available = true;
          }
          catch (Throwable t2) {
            LOG.warn("Failed to load native filesystem for Windows", t2);
          }
        }
      }
    }

    IdeaWin32 instance = null;
    if (available) {
      try {
        instance = new IdeaWin32();
        LOG.info("Native filesystem for Windows is operational");
      }
      catch (Throwable t) {
        LOG.warn("Failed to initialize native filesystem for Windows", t);
      }
    }
    ourInstance = instance;
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
  public FileInfo getInfo(@NotNull final String path) {
    return getInfo0(unc(path));
  }

  @Nullable
  public String resolveSymLink(@NotNull final String path) {
    final String result = resolveSymLink0(unc(path));
    return result != null && result.startsWith(PATH_PREFIX) ? result.substring(PREFIX_SIZE) : result;
  }

  @Nullable
  public FileInfo[] listChildren(@NotNull final String path) {
    return listChildren0(unc(path) + PATH_SUFFIX);
  }

  private static String unc(final String path) {
    return path.length() < MAX_PATH ? path : PATH_PREFIX + path;
  }

  private native FileInfo getInfo0(String path);

  private native String resolveSymLink0(String path);

  private native FileInfo[] listChildren0(String path);
}
