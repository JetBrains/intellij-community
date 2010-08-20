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
package com.intellij.ui.wizard;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class WizardStep<T extends WizardModel> {

  public static final WizardStep FORCED_GOAL_DROPPED = new Empty();
  public static final WizardStep FORCED_GOAL_ACHIEVED = new Empty();

  private String myTitle = "";
  private String myExplanation = "";
//todo:
  private Icon myIcon = IconLoader.getIcon("/newprojectwizard.png");
  private String myHelpId;

  protected WizardStep() {
  }

  public WizardStep(String title) {
    myTitle = title;
  }

  public WizardStep(String title, String explanation) {
    myTitle = title;
    myExplanation = explanation;
  }

  public WizardStep(String title, String explanation, Icon icon) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
  }

  public WizardStep(String title, String explanation, Icon icon, String helpId) {
    myTitle = title;
    myExplanation = explanation;
    myIcon = icon;
    myHelpId = helpId;
  }

  public String getTitle() {
    return myTitle;
  }

  public String getExplanation() {
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

  public String getHelpId() {
    return myHelpId;
  }

  public static class Empty extends WizardStep {
    public JComponent prepare(WizardNavigationState state) {
      return null;
    }
  }

}