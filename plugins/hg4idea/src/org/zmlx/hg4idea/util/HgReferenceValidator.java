/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public abstract class HgReferenceValidator implements InputValidatorEx {

  protected final HgRepository myRepository;
  protected String myErrorText;

  protected HgReferenceValidator(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  @Override
  public boolean checkInput(@Nullable String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return false;
    }
    if (name.contains(":")) {
      myErrorText = "Name could not contain colons";
      return false;
    }
    return !isReservedWord(name) && !hasConflictsWithAnotherNames(name);
  }

  @Override
  public boolean canClose(@Nullable String name) {
    return checkInput(name);
  }

  private boolean isReservedWord(@Nullable String name) {
    myErrorText = TIP_REFERENCE.equals(name) ? String.format("The name \'%s\' is reserved.", name) : null;
    return myErrorText != null;
  }

  protected abstract boolean hasConflictsWithAnotherNames(@Nullable String name);

  @Nullable
  @Override
  public String getErrorText(@Nullable String inputString) {
    return myErrorText;
  }
}
