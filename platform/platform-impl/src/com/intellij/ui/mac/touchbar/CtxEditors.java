// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

class CtxEditors {
  private static Map<Editor, WeakReference<Component>> ourEditors = null;
  private static Map<Long, ActionGroup> ourEditorSearchActions = null;
  private static Customizer ourCustomizer = null;

  private static void initialize() {
    if (ourEditors != null)
      return;

    ourEditors = new WeakHashMap<>();

    ourEditorSearchActions = ActionsLoader.getActionGroup("EditorSearch");
    if (ourEditorSearchActions == null) {
      Logger.getInstance(CtxEditors.class).debug("null action group for editor-search");
      return;
    }

    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        final WeakReference<Component> cmpRef = ourEditors.remove(event.getEditor());
        final Component cmp = cmpRef != null ? cmpRef.get() : null;
        if (cmp != null) {
          TouchBarsManager.unregister(cmp);
        }
      }
    }, ApplicationManager.getApplication());

    ourCustomizer = new Customizer(new TBPanel.CrossEscInfo(false, false)/*always replace esc for editor search*/, null);
  }

  static void onUpdateEditorHeader(@NotNull Editor editor) {
    initialize();
    if (ourEditorSearchActions == null) {
      return;
    }

    // register editor
    final @Nullable Component newCmp = editor.getHeaderComponent();
    final @Nullable WeakReference<Component> oldCmpRef = ourEditors.put(editor, new WeakReference<>(newCmp));
    final @Nullable Component oldCmp = oldCmpRef != null ? oldCmpRef.get() : null;
    if (oldCmp != null) {
      TouchBarsManager.unregister(oldCmp);
    }
    if (newCmp != null) {
      TouchBarsManager.register(newCmp, ourEditorSearchActions, ourCustomizer);
    }
  }
}
