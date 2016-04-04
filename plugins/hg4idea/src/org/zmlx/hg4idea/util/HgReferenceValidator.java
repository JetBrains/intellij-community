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

import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public class HgReferenceValidator implements InputValidatorEx {
  protected String myErrorText;
  private static final HgReferenceValidator INSTANCE = new HgReferenceValidator();

  private static final Pattern DIGITS_ILLEGAL = Pattern.compile("[0-9]*");  // reference names couldn't contain only digits
  private static final Pattern ILLEGAL = Pattern.compile(
    "[:]"                                // contains ':' character
  );

  public static HgReferenceValidator getInstance() {
    return INSTANCE;
  }

  protected HgReferenceValidator() {
  }

  @Override
  public boolean checkInput(String inputString) {
    if (StringUtil.isEmptyOrSpaces(inputString)) {
      return false;
    }
    if (containsIllegalSymbols(inputString)) return false;
    return !isReservedWord(inputString) && !onlyDigits(inputString) && !hasConflictsWithAnotherNames(inputString);
  }

  protected boolean containsIllegalSymbols(@Nullable String inputString) {
    if (inputString != null && ILLEGAL.matcher(inputString).find()) {
      myErrorText = "Name could not contain colons";
      return true;
    }
    return false;
  }

  private boolean onlyDigits(@Nullable String inputString) {
    if (inputString != null && DIGITS_ILLEGAL.matcher(inputString).matches()) {
      myErrorText = "Invalid name for hg reference";
      return true;
    }
    return false;
  }

  @Override
  public boolean canClose(@Nullable String name) {
    return checkInput(name);
  }

  private boolean isReservedWord(@Nullable String name) {
    myErrorText = TIP_REFERENCE.equals(name) ? String.format("The name \'%s\' is reserved.", name) : null;
    return myErrorText != null;
  }

  protected boolean hasConflictsWithAnotherNames(@Nullable String name) {
    return false;
  }

  @Nullable
  @Override
  public String getErrorText(@Nullable String inputString) {
    return myErrorText;
  }

  @NotNull
  public String cleanUpBranchName(@NotNull String branchName) {
    if (onlyDigits(branchName)) return branchName + "_";
    return branchName.replaceAll(ILLEGAL.pattern(), "_");
  }
}
