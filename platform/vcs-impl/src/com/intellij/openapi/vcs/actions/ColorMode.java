// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import static com.intellij.openapi.vcs.VcsBundle.BUNDLE;

@ApiStatus.Internal
public enum ColorMode {
  AUTHOR("author", "annotations.color.mode.author"),
  ORDER("order", "annotations.color.mode.order"),
  NONE("none", "annotations.color.mode.hide");

  private static final String KEY = "annotate.color.mode"; //NON-NLS
  private final String myId;
  private final String myDescriptionKey;

  ColorMode(@NotNull @NonNls String id,
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String descriptionKey) {
    myId = id;
    myDescriptionKey = descriptionKey;
  }

  public @NlsActions.ActionText String getDescription() {
    return VcsBundle.message(myDescriptionKey);
  }

  boolean isSet() {
    return myId.equals(PropertiesComponent.getInstance().getValue(KEY));
  }

  void set() {
    PropertiesComponent.getInstance().setValue(KEY, myId);
  }
}
