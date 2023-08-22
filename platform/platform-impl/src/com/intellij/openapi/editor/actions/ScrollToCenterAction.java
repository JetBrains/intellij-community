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

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScrollToCenterAction extends InactiveEditorAction {
  public ScrollToCenterAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      boolean savedSetting = EditorSettingsExternalizable.getInstance().isRefrainFromScrolling();
      boolean overriddenInEditor = false;
      try {
        EditorSettingsExternalizable.getInstance().setRefrainFromScrolling(false);
        if (editor.getSettings().isRefrainFromScrolling()) {
          overriddenInEditor = true;
          editor.getSettings().setRefrainFromScrolling(false);
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
      finally {
        EditorSettingsExternalizable.getInstance().setRefrainFromScrolling(savedSetting);
        if (overriddenInEditor) {
          editor.getSettings().setRefrainFromScrolling(true);
        }
      }
    }
  }
}
