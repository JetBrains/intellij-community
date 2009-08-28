package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.ex.EditorEx;

public interface EditorSource {
  FragmentSide getSide();
  DiffContent getContent();

  EditorSource NULL = new EditorSource() {
    public EditorEx getEditor() {
      return null;
    }

    public void addDisposable(Disposable disposable) {
      Logger.getInstance("#com.intellij.openapi.diff.impl.EditorSource").assertTrue(false);
    }

    public FragmentSide getSide() {
      return null;
    }

    public DiffContent getContent() {
      return null;
    }
  };

  EditorEx getEditor();

  void addDisposable(Disposable disposable);
}
