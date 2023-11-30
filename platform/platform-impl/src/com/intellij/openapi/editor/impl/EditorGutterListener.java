// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.impl.event.EditorGutterHoverEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface EditorGutterListener extends EventListener {
  default void textAnnotationAdded(@NotNull TextAnnotationGutterProvider provider) { }

  default void textAnnotationRemoved(@NotNull TextAnnotationGutterProvider provider) { }

  @ApiStatus.Experimental
  default void hoverStarted(@NotNull EditorGutterHoverEvent event) { }

  @ApiStatus.Experimental
  default void hoverEnded(@NotNull EditorGutterHoverEvent event) { }

  default void lineNumberConvertersChanged() { }
}
