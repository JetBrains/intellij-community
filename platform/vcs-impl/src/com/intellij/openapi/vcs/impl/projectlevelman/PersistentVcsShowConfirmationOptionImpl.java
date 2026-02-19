// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.VcsAbstractSetting;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PersistentVcsShowConfirmationOptionImpl extends VcsAbstractSetting implements PersistentVcsShowConfirmationOption {
  private final VcsConfiguration.StandardConfirmation myConfirmation;
  private final OptionsAndConfirmations myOptions;

  public PersistentVcsShowConfirmationOptionImpl(@NotNull VcsConfiguration.StandardConfirmation confirmation,
                                                 @NotNull OptionsAndConfirmations options) {
    myConfirmation = confirmation;
    myOptions = options;
  }

  @Override
  public @NonNls @NotNull String getId() {
    return myConfirmation.getId();
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return myConfirmation.getDisplayName();
  }

  @Override
  public Value getValue() {
    return myOptions.getConfirmationValue(myConfirmation.getId());
  }

  @Override
  public void setValue(Value value) {
    myOptions.setConfirmationValue(myConfirmation.getId(), value);
  }

  @Override
  public boolean isPersistent() {
    return true;
  }
}
