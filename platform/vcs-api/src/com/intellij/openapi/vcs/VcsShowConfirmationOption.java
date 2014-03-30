/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

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

    @Deprecated
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

  Value getValue();
  void setValue(Value value);
  boolean isPersistent();
}
