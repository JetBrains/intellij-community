// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorHintFixture implements EditorHintListener {
  private LightweightHint myCurrentHint;
  
  public EditorHintFixture(Disposable parentDisposable) {
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(EditorHintListener.TOPIC, this);
  }

  @Override
  public void hintShown(@NotNull Editor editor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
    hint.putUserData(LightweightHint.SHOWN_AT_DEBUG, Boolean.TRUE);
    myCurrentHint = hint;
    hint.addHintListener(event -> {
      LightweightHint source = (LightweightHint)event.getSource();
      source.putUserData(LightweightHint.SHOWN_AT_DEBUG, null);
      if (source == myCurrentHint) myCurrentHint = null;
    });
  }
  
  public @Nullable String getCurrentHintText() {
    return myCurrentHint == null ? null : removeCurrentParameterColor(myCurrentHint.getComponent().toString());
  }

  public static String removeCurrentParameterColor(String text) {
    return text == null ? null : text.replace("<b color=1d1d1d>", "<b>");
  }
}
