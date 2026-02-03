// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class EditorActionPlan implements ActionPlan {
  private ImmutableCharSequence myText;
  private final Editor myEditor;
  private int myCaretOffset;
  private final List<Replacement> myReplacements = new ArrayList<>();

  EditorActionPlan(@NotNull Editor editor) {
    myEditor = editor;
    CharSequence sequence = editor.getDocument().getImmutableCharSequence();
    myText = CharArrayUtil.createImmutableCharSequence(sequence);
    myCaretOffset = editor.getCaretModel().getOffset();
  }

  @Override
  public @NotNull ImmutableCharSequence getText() {
    return myText;
  }

  @Override
  public void replace(int begin, int end, String s) {
    myText = myText.replace(begin, end, s);
    myReplacements.add(new Replacement(begin, end, s));
    if (myCaretOffset == end) {
      myCaretOffset += s.length() - (end - begin);
    }
  }

  @Override
  public int getCaretOffset() {
    return myCaretOffset;
  }

  @Override
  public void setCaretOffset(int offset) {
    myCaretOffset = offset;
  }

  public List<Replacement> getReplacements() {
    return Collections.unmodifiableList(myReplacements);
  }

  public int getCaretShift() {
    return myCaretOffset - myEditor.getCaretModel().getOffset();
  }

  static final class Replacement {
    private final int myBegin;
    private final int myEnd;
    private final String myText;

    Replacement(int begin, int end, String text) {
      myBegin = begin;
      myEnd = end;
      myText = text;
    }

    public int getBegin() {
      return myBegin;
    }

    public int getEnd() {
      return myEnd;
    }

    public String getText() {
      return myText;
    }
  }
}
