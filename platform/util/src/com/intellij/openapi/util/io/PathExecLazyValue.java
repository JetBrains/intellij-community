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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class PathExecLazyValue extends AtomicNotNullLazyValue<Boolean> {
  private final String myName;

  public PathExecLazyValue(@NotNull String name) {
    if (StringUtil.containsAnyChar(name, "/\\")) {
      throw new IllegalArgumentException(name);
    }
    myName = name;
  }

  @NotNull
  @Override
  protected Boolean compute() {
    String path = EnvironmentUtil.getValue("PATH");
    if (path != null) {
      for (String dir : StringUtil.tokenize(path, File.pathSeparator)) {
        if (new File(dir, myName).canExecute()) {
          return true;
        }
      }
    }

    return false;
  }
}