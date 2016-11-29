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
package com.intellij.openapi.vcs;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class VirtualFileStreamRule implements GetDataRule {
  @Nullable
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataProvider);
    if (files != null) {
      return Stream.of(files);
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataProvider);
    if (file != null) {
      return Stream.of(file);
    }

    return null;
  }
}