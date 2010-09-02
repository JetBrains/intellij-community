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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

public interface EditorMarkupModel extends MarkupModel {
  @NotNull Editor getEditor();

  void setErrorStripeVisible(boolean val);

  void setErrorStripeRenderer(ErrorStripeRenderer renderer);
  ErrorStripeRenderer getErrorStripeRenderer();

  void addErrorMarkerListener(@NotNull ErrorStripeListener listener);
  void removeErrorMarkerListener(@NotNull ErrorStripeListener listener);

  void setErrorPanelPopupHandler(@NotNull PopupHandler handler);

  void setErrorStripTooltipRendererProvider(@NotNull ErrorStripTooltipRendererProvider provider);

  @NotNull
  ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider();

  void setMinMarkHeight(int minMarkHeight);
}
