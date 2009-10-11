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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class IgnoredBeanFactory {
  private IgnoredBeanFactory() {
  }

  public static IgnoredFileBean ignoreUnderDirectory(final @NonNls String path, Project p) {
    final String correctedPath = (path.endsWith("/") || path.endsWith(File.separator)) ? path : path + "/";
    return new IgnoredFileBean(correctedPath, IgnoreSettingsType.UNDER_DIR, p);
  }

  public static IgnoredFileBean ignoreFile(final @NonNls String path, Project p) {
    // todo check??
    return new IgnoredFileBean(path, IgnoreSettingsType.FILE, p);
  }

  public static IgnoredFileBean withMask(final String mask) {
    return new IgnoredFileBean(mask);
  }
}
