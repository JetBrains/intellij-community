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
package com.intellij.execution.testframework.sm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;
import java.util.Collections;

/**
 * @author Roman Chernyatchik
 */
public class LocationProviderUtil {
  @NonNls private static final String PROTOCOL_SEPARATOR = "://";

  private LocationProviderUtil() {
  }

  @Nullable
  public static String extractProtocol(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(0, index);
    }
    return null;
  }

  @Nullable
  public static String extractPath(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(index + PROTOCOL_SEPARATOR.length());
    }
    return null;
  }

  public static List<VirtualFile> findSuitableFilesFor(final String filePath) {
    final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (fileByPath != null) {
      return Collections.singletonList(fileByPath);
    }
    // if we are in UnitTest mode probably TempFileSystem is used instead of LocaFileSystem
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final VirtualFile tempFileByPath = TempFileSystem.getInstance().findFileByPath(filePath);
      return Collections.singletonList(tempFileByPath);
    }
    return Collections.emptyList();
  }
}
