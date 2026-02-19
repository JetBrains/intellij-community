// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.VcsAbstractSetting;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class LegacyVcsShowOptionsSettingImpl extends VcsAbstractSetting implements PersistentVcsShowSettingOption {
  private final String myId;
  private final OptionsAndConfirmations myOptions;

  public LegacyVcsShowOptionsSettingImpl(@NonNls @NotNull String id, @NotNull OptionsAndConfirmations options) {
    myId = id;
    myOptions = options;
  }

  @Override
  public @NonNls @NotNull String getId() {
    return myId;
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return myId; //NON-NLS compatibility with old plugins
  }

  @Override
  public boolean getValue() {
    return myOptions.getOptionValue(myId);
  }

  @Override
  public void setValue(boolean value) {
    myOptions.setOptionValue(myId, value);
  }
}
