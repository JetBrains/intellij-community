// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import org.jetbrains.annotations.NotNull;

public interface GitRefNameValidatorSettingsInterface {
  boolean isOn();

  void setOn(boolean isOn);

  @NotNull GitRefNameValidatorReplacementOption getReplacementOption();

  void setReplacementOption(GitRefNameValidatorReplacementOption replacementOption);

  boolean isConvertingToLowerCase();

  void setConvertingToLowerCase(boolean isConvertingToLowerCase);

  int getMaxNumberOfConsecutiveUnderscores();

  void setMaxNumberOfConsecutiveUnderscores(int maxNumberOfConsecutiveUnderscores);
}
