// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.wizard;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class WizardStep<T extends WizardModel> {

  public static final WizardStep FORCED_GOAL_DROPPED = new Empty();
  public static final WizardStep FORCED_GOAL_ACHIEVED = new Empty();

  private @NlsContexts.DialogTitle String myTitle = "";
  private @NlsContexts.DetailedDescription String myExplanation = "";
//todo:
  private Icon myIcon = null;
  private String myHelpId;

  protected WizardStep() {
  }

  public WizardStep(@NlsContexts.DialogTitle String title) {
    myTitle = title;
  }

  public WizardStep(@NlsContexts.DialogTitle String title, @NlsContexts.DetailedDescription String explanation) {
    myTitle = title;
    myExplanation = explanation;
  }

  public WizardStep(@NlsContexts.DialogTitle String title, @NlsContexts.DetailedDescription String explanation, Icon icon) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
  }

  public WizardStep(@NlsContexts.DialogTitle String title, @NlsContexts.DetailedDescription String explanation, Icon icon, @NonNls String helpId) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
    myHelpId = helpId;
  }

  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  public @NlsContexts.DetailedDescription String getExplanation() {
    return myExplanation;
  }

  public abstract JComponent prepare(WizardNavigationState state);

  public WizardStep onNext(T model) {
    return model.getNextFor(this);
  }

  public WizardStep onPrevious(T model) {
    return model.getPreviousFor(this);
  }

  public boolean onCancel() {
    return true;
  }

  public boolean onFinish() {
    return true;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NonNls
  public String getHelpId() {
    return myHelpId;
  }

  public static class Empty extends WizardStep {
    @Override
    public JComponent prepare(WizardNavigationState state) {
      return null;
    }
  }

}