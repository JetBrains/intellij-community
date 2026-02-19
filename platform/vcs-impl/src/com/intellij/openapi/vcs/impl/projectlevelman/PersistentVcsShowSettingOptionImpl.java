// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.VcsAbstractSetting;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PersistentVcsShowSettingOptionImpl extends VcsAbstractSetting implements PersistentVcsShowSettingOption {
  private final VcsConfiguration.StandardOption myOption;
  private final OptionsAndConfirmations myOptions;

  public PersistentVcsShowSettingOptionImpl(@NotNull VcsConfiguration.StandardOption option,
                                            @NotNull OptionsAndConfirmations options) {
    myOption = option;
    myOptions = options;
  }

  @Override
  public @NonNls @NotNull String getId() {
    return myOption.getId();
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return myOption.getDisplayName();
  }

  @Override
  public boolean getValue() {
    return myOptions.getOptionValue(myOption.getId());
  }

  @Override
  public void setValue(boolean value) {
    myOptions.setOptionValue(myOption.getId(), value);
  }
}
