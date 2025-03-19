// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.util;

import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;

import java.util.regex.Pattern;

import static org.zmlx.hg4idea.util.HgUtil.TIP_REFERENCE;

public class HgReferenceValidator implements InputValidatorEx {
  protected @Nls String myErrorText;
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
      myErrorText = HgBundle.message("hg4idea.validation.name.no.colons");
      return true;
    }
    return false;
  }

  private boolean onlyDigits(@Nullable String inputString) {
    if (inputString != null && DIGITS_ILLEGAL.matcher(inputString).matches()) {
      myErrorText = HgBundle.message("hg4idea.validation.name.invalid");
      return true;
    }
    return false;
  }

  @Override
  public boolean canClose(@Nullable String name) {
    return checkInput(name);
  }

  private boolean isReservedWord(@Nullable String name) {
    myErrorText = TIP_REFERENCE.equals(name) ? HgBundle.message("hg4idea.validation.name.reserved", name) : null;
    return myErrorText != null;
  }

  protected boolean hasConflictsWithAnotherNames(@Nullable String name) {
    return false;
  }

  @Override
  public @Nullable String getErrorText(@Nullable String inputString) {
    return myErrorText;
  }

  public @NotNull String cleanUpBranchName(@NotNull String branchName) {
    if (onlyDigits(branchName)) return branchName + "_";
    return branchName.replaceAll(ILLEGAL.pattern(), "_").replaceAll("\"", "");
  }
}
