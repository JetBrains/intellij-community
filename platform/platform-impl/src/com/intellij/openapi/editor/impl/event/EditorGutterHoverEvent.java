// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

@ApiStatus.Experimental
public final class EditorGutterHoverEvent extends EventObject {

  private final @NotNull GutterIconRenderer myGutterIconRenderer;

  public EditorGutterHoverEvent(@NotNull EditorGutterComponentEx editorGutterComponentEx, @NotNull GutterIconRenderer renderer)
  {
    super(editorGutterComponentEx);
    myGutterIconRenderer = renderer;
  }

  public @NotNull GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }
}
