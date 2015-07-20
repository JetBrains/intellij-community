/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;

interface OptionsEditorColleague {
  ActionCallback onSelected(@Nullable Configurable configurable, final Configurable oldConfigurable);

  ActionCallback onModifiedAdded(final Configurable configurable);

  ActionCallback onModifiedRemoved(final Configurable configurable);

  ActionCallback onErrorsChanged();

  class Adapter implements OptionsEditorColleague {
    public ActionCallback onSelected(@Nullable final Configurable configurable, final Configurable oldConfigurable) {
      return ActionCallback.DONE;
    }

    public ActionCallback onModifiedAdded(final Configurable configurable) {
      return ActionCallback.DONE;
    }

    public ActionCallback onModifiedRemoved(final Configurable configurable) {
      return ActionCallback.DONE;
    }

    public ActionCallback onErrorsChanged() {
      return ActionCallback.DONE;
    }
  }

}