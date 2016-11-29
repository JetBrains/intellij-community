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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PatchNameChecker {
  public final static int MAX = 100;
  private final static int MAX_PATH = 255; // Windows path len restrictions

  @Nullable
  public static String validateName(@NotNull String name) {
    String fileName = new File(name).getName();
    if (StringUtil.isEmptyOrSpaces(fileName)) {
      return "File name cannot be empty";
    }
    else if (name.length() > MAX_PATH) {
      return "File path should not be too long.";
    }
    return null;
  }
}
