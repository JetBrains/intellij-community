// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

public interface OptionsEditorColleague {
  ActionCallback onSelected(@Nullable Configurable configurable, final Configurable oldConfigurable);

  ActionCallback onModifiedAdded(final Configurable configurable);

  ActionCallback onModifiedRemoved(final Configurable configurable);

  ActionCallback onErrorsChanged();

  class Adapter implements OptionsEditorColleague {
    @Override
    public ActionCallback onSelected(@Nullable final Configurable configurable, final Configurable oldConfigurable) {
      return ActionCallback.DONE;
    }

    @Override
    public ActionCallback onModifiedAdded(final Configurable configurable) {
      return ActionCallback.DONE;
    }

    @Override
    public ActionCallback onModifiedRemoved(final Configurable configurable) {
      return ActionCallback.DONE;
    }

    @Override
    public ActionCallback onErrorsChanged() {
      return ActionCallback.DONE;
    }
  }

}