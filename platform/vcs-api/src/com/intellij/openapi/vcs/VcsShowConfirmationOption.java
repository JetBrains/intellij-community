// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface VcsShowConfirmationOption {

  enum Value {
    // NB: don't reorder enum values, otherwise you may break user settings based on the obsolete implementation
    SHOW_CONFIRMATION,
    DO_NOTHING_SILENTLY,
    DO_ACTION_SILENTLY;

    public String toString() {
      // compatibility with the old implementation
      return String.valueOf(ordinal());
    }

    public static Value fromString(@NotNull String s){
      if (s.equals("1")) return DO_NOTHING_SILENTLY;
      if (s.equals("2")) return DO_ACTION_SILENTLY;
      return SHOW_CONFIRMATION;
    }
  }

  VcsShowConfirmationOption STATIC_SHOW_CONFIRMATION = new VcsShowConfirmationOption() {
    @Override
    public Value getValue() {
      return Value.SHOW_CONFIRMATION;
    }
    @Override
    public void setValue(Value value) {
    }
    @Override
    public boolean isPersistent() {
      return false;
    }
  };

  @NotNull
  @Nls
  static String getConfirmationOptionText(@NotNull VcsShowConfirmationOption.Value value) {
    return VcsBundle.message(switch (value) {
      case SHOW_CONFIRMATION -> "settings.confirmation.option.text.ask";
      case DO_NOTHING_SILENTLY -> "settings.confirmation.option.text.no";
      case DO_ACTION_SILENTLY -> "settings.confirmation.option.text.yes";
    });
  }

  Value getValue();
  void setValue(Value value);
  boolean isPersistent();
}
