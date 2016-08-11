/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TextComponentInlayModel implements InlayModel {
  @Nullable
  @Override
  public Inlay addElement(int offset, @NotNull Inlay.Type type, @NotNull Inlay.Renderer renderer) {
    return null;
  }

  @NotNull
  @Override
  public List<Inlay> getElementsInRange(int startOffset, int endOffset, @NotNull Inlay.Type type) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Inlay> getVisibleElements(@NotNull Inlay.Type type) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Inlay> getLineExtendingElements() {
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlayAt(@NotNull VisualPosition visualPosition) {
    return false;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
  }
}
