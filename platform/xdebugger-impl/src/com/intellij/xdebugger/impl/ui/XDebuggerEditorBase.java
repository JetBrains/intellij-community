/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerEditorBase {
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;
  @NotNull private final EvaluationMode myMode;
  @Nullable private final String myHistoryId;
  private final XSourcePosition mySourcePosition;
  private int myHistoryIndex;

  protected XDebuggerEditorBase(final Project project,
                                @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                                @NotNull EvaluationMode mode,
                                @Nullable @NonNls String historyId,
                                final @Nullable XSourcePosition sourcePosition) {
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
    myMode = mode;
    myHistoryId = historyId;
    mySourcePosition = sourcePosition;
  }

  @NotNull
  public EvaluationMode getMode() {
    return myMode;
  }

  public abstract JComponent getComponent();

  protected abstract void doSetText(String text);

  public void setText(String text) {
    saveTextInHistory(text);
    doSetText(text);
  }

  public abstract String getText();

  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  public abstract void selectAll();

  protected void onHistoryChanged() {
  }

  protected List<String> getRecentExpressions() {
    if (myHistoryId != null) {
      return XDebuggerHistoryManager.getInstance(myProject).getRecentExpressions(myHistoryId);
    }
    return Collections.emptyList();
  }

  public void saveTextInHistory() {
    saveTextInHistory(getText());
  }

  private void saveTextInHistory(final String text) {
    if (myHistoryId != null) {
      XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(myHistoryId, text);
      myHistoryIndex = 0;
      onHistoryChanged();
    }
  }

  public XDebuggerEditorsProvider getEditorsProvider() {
    return myDebuggerEditorsProvider;
  }

  public Project getProject() {
    return myProject;
  }

  protected Document createDocument(final String text) {
    return getEditorsProvider().createDocument(getProject(), text, mySourcePosition, myMode);
  }

  public boolean canGoBackward() {
    return myHistoryIndex < getRecentExpressions().size()-1;
  }

  public boolean canGoForward() {
    return myHistoryIndex > 0;
  }

  public void goBackward() {
    final List<String> expressions = getRecentExpressions();
    if (myHistoryIndex < expressions.size() - 1) {
      myHistoryIndex++;
      doSetText(expressions.get(myHistoryIndex));
    }
  }

  public void goForward() {
    final List<String> expressions = getRecentExpressions();
    if (myHistoryIndex > 0) {
      myHistoryIndex--;
      doSetText(expressions.get(myHistoryIndex));
    }
  }
}
