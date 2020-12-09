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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class EditorActionPlan implements ActionPlan {
  private ImmutableCharSequence myText;
  private final Editor myEditor;
  private int myCaretOffset;
  private final List<Replacement> myReplacements = new ArrayList<>();

  EditorActionPlan(@NotNull Editor editor) {
    myEditor = editor;
    myText = (ImmutableCharSequence)editor.getDocument().getImmutableCharSequence();
    myCaretOffset = editor.getCaretModel().getOffset();
  }

  @NotNull
  @Override
  public ImmutableCharSequence getText() {
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

  static class Replacement {
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
