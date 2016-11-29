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
package org.jetbrains.io;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class LocalFileFinder {
  // if java.io.File.exists() takes more time than this timeout we assume that this is network drive and do not ping it any more
  private static final int FILE_EXISTS_MAX_TIMEOUT_MILLIS = 10;

  private static final Cache<Character, Boolean> myWindowsDrivesMap = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

  private LocalFileFinder() {
  }

  /**
   Behavior is not predictable and result is not guaranteed:
   1) result of check cached, but cache entry invalidated on timeout (5 min after write), not on actual change of drive status.
   2) even if drive exists, it could be not used due to 10 ms threshold.
   Method is not generic and is not suitable for all.
   */
  @Nullable
  public static VirtualFile findFile(@NotNull String path) {
    if (windowsDriveExists(path)) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return null;
  }

  public static boolean windowsDriveExists(@NotNull String path) {
    if (!SystemInfo.isWindows) return true;
    
    if (path.length() > 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
      final char driveLetter = Character.toUpperCase(path.charAt(0));
      final Boolean driveExists = myWindowsDrivesMap.getIfPresent(driveLetter);
      if (driveExists != null) {
        return driveExists;
      }
      else {
        final long t0 = System.currentTimeMillis();
        boolean exists = new File(driveLetter + ":" + File.separator).exists();
        if (System.currentTimeMillis() - t0 > FILE_EXISTS_MAX_TIMEOUT_MILLIS) {
          exists = false; // may be a slow network drive
        }

        myWindowsDrivesMap.put(driveLetter, exists);
        return exists;
      }
    }

    return false;
  }
}