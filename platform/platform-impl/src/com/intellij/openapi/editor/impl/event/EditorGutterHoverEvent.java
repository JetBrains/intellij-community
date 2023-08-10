package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

@ApiStatus.Experimental
public class EditorGutterHoverEvent extends EventObject {

  @NotNull
  private final GutterIconRenderer myGutterIconRenderer;

  public EditorGutterHoverEvent(@NotNull EditorGutterComponentEx editorGutterComponentEx, @NotNull GutterIconRenderer renderer)
  {
    super(editorGutterComponentEx);
    myGutterIconRenderer = renderer;
  }

  public @NotNull GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }
}
