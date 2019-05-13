/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgPatchReferenceValidator extends HgReferenceValidator {
  private final HgRepository myRepository;

  public HgPatchReferenceValidator(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  @Override
  public boolean checkInput(@Nullable String name) {
    return !StringUtil.isEmptyOrSpaces(name) && !containsIllegalSymbols(name) && !hasConflictsWithAnotherNames(name);
  }

  @Override
  protected boolean hasConflictsWithAnotherNames(@Nullable String name) {
    myErrorText = myRepository.getAllPatchNames().contains(name)
                  ? String.format("A patch with the \'%s\' name already exists", name) : null;
    return myErrorText != null;
  }
}
