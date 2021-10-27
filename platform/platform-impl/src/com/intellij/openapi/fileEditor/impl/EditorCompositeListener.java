// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface EditorCompositeListener extends EventListener {
  default void isPinnedChanged(boolean value) { }

  default void isPreviewChanged(boolean value) { }

  default void editorAdded(@NotNull FileEditorWithProvider editorWithProvider) { }

  default void displayNameChanged(@NotNull FileEditor editor, @NotNull @NlsContexts.TabTitle String name) { }

  default void topComponentAdded(@NotNull FileEditor editor, int index, @NotNull JComponent component) { }

  default void topComponentRemoved(@NotNull FileEditor editor, @NotNull JComponent component) { }

  default void bottomComponentAdded(@NotNull FileEditor editor, int index, @NotNull JComponent component) { }

  default void bottomComponentRemoved(@NotNull FileEditor editor, @NotNull JComponent component) { }
}
