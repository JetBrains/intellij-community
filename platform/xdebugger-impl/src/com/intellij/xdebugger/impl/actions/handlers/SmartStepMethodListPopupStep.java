// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.*;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

class SmartStepMethodListPopupStep<V extends XSmartStepIntoVariant> implements ListPopupStep<V> {
  @NotNull
  private final List<V> myTargets;
  @NotNull
  private final String myTitle;
  private final XSmartStepIntoHandler<V> myHandler;
  private final XDebugSession mySession;
  private final ScopeHighlighter myScopeHighlighter;

  public interface OnChooseRunnable {
    void execute(XSmartStepIntoVariant stepTarget);
  }

  SmartStepMethodListPopupStep(@NotNull final String title,
                               @NotNull final Editor editor,
                               @NotNull final List<V> targets,
                               @NotNull final XDebugSession session,
                               @NotNull final XSmartStepIntoHandler<V> handler) {
    myTargets = targets;
    myScopeHighlighter = new ScopeHighlighter(editor);
    myTitle = title;
    myHandler = handler;
    mySession = session;
  }

  @NotNull
  public ScopeHighlighter getScopeHighlighter() {
    return myScopeHighlighter;
  }

  @Override
  @NotNull
  public List<V> getValues() {
    return myTargets;
  }

  @Override
  public boolean isSelectable(V value) {
    return true;
  }

  @Override
  public Icon getIconFor(V avalue) {
    return avalue.getIcon();
  }

  @Override
  @NotNull
  public String getTextFor(V value) {
    return value.getText();
  }

  @Override
  public ListSeparator getSeparatorAbove(V value) {
    return null;
  }

  @Override
  public int getDefaultOptionIndex() {
    return 0;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public PopupStep onChosen(V selectedValue, final boolean finalChoice) {
    if (finalChoice) {
      myScopeHighlighter.dropHighlight();

      mySession.smartStepInto(myHandler, selectedValue);
    }
    return FINAL_CHOICE;
  }

  @Override
  public Runnable getFinalRunnable() {
    return null;
  }

  @Override
  public boolean hasSubstep(V selectedValue) {
    return false;
  }

  @Override
  public void canceled() {
    myScopeHighlighter.dropHighlight();
  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  @Override
  public MnemonicNavigationFilter<V> getMnemonicNavigationFilter() {
    return null;
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public SpeedSearchFilter<V> getSpeedSearchFilter() {
    return this::getTextFor;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return false;
  }
}
