/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ErrorStripeEditorCustomization extends SimpleEditorCustomization {

  public static final ErrorStripeEditorCustomization ENABLED = new ErrorStripeEditorCustomization(true);
  public static final ErrorStripeEditorCustomization DISABLED = new ErrorStripeEditorCustomization(false);

  private ErrorStripeEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    if (isEnabled()) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
      editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    }
    else {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(false);
    }
  }
}
