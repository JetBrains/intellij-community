package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;

import java.util.EventListener;

/**
 * @author max
 */
public interface FocusChangeListener extends EventListener {
  void focusGained(Editor editor);
  void focusLost(Editor editor);
}
