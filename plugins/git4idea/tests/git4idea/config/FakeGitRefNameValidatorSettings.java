// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import org.jetbrains.annotations.NotNull;

public class FakeGitRefNameValidatorSettings implements GitRefNameValidatorSettingsInterface {
  protected boolean myIsOn;
  @NotNull
  protected GitRefNameValidatorReplacementOption myReplacementOption;
  protected boolean myIsConvertingToLowerCase;
  protected int myMaxNumberOfConsecutiveUnderscores;

  protected void resetSettings() {
    this.setOn(true);
    this.setReplacementOption(GitRefNameValidatorReplacementOption.HYPHEN);
    this.setConvertingToLowerCase(true);
    this.setMaxNumberOfConsecutiveUnderscores(2);
  }

  public FakeGitRefNameValidatorSettings() {
    resetSettings();
  }

  @Override
  public boolean isOn() {
    return myIsOn;
  }

  @Override
  public void setOn(boolean isOn) {
    myIsOn = isOn;
  }

  @Override
  public @NotNull GitRefNameValidatorReplacementOption getReplacementOption() {
    return myReplacementOption;
  }

  @Override
  public void setReplacementOption(@NotNull GitRefNameValidatorReplacementOption replacementOption) {
    myReplacementOption = replacementOption;
  }

  @Override
  public boolean isConvertingToLowerCase() {
    return myIsConvertingToLowerCase;
  }

  @Override
  public void setConvertingToLowerCase(boolean isConvertingToLowerCase) {
    myIsConvertingToLowerCase = isConvertingToLowerCase;
  }

  @Override
  public int getMaxNumberOfConsecutiveUnderscores() {
    return myMaxNumberOfConsecutiveUnderscores;
  }

  @Override
  public void setMaxNumberOfConsecutiveUnderscores(int maxNumberOfConsecutiveUnderscores) {
    myMaxNumberOfConsecutiveUnderscores = maxNumberOfConsecutiveUnderscores;
  }
}
