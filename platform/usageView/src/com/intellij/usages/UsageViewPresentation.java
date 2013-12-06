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
package com.intellij.usages;

import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UsageViewPresentation {
  private String myTabText;
  private String myScopeText;
  private String myUsagesString;
  private String myTargetsNodeText = UsageViewBundle.message("node.targets"); // Default value. to be overwritten in most cases.
  private String myNonCodeUsagesString = UsageViewBundle.message("node.non.code.usages");
  private String myCodeUsagesString = UsageViewBundle.message("node.found.usages");
  private String myUsagesInGeneratedCodeString = UsageViewBundle.message("node.usages.in.generated.code");
  private boolean myShowReadOnlyStatusAsRed = false;
  private boolean myShowCancelButton = false;
  private boolean myOpenInNewTab = true;
  private boolean myCodeUsages = true;
  private String myUsagesWord = UsageViewBundle.message("usage.name");

  private String myTabName;
  private String myToolwindowTitle;

  private List<Action> myNotFoundActions;
  private boolean myDetachedMode; // no UI will be shown
  private String myDynamicCodeUsagesString;
  private boolean myMergeDupLinesAvailable = true;

  public String getTabText() {
    return myTabText;
  }

  public void setTabText(String tabText) {
    myTabText = tabText;
  }

  public String getScopeText() {
    return myScopeText;
  }

  public void setScopeText(String scopeText) {
    myScopeText = scopeText;
  }

  public boolean isShowReadOnlyStatusAsRed() {
    return myShowReadOnlyStatusAsRed;
  }

  public void setShowReadOnlyStatusAsRed(boolean showReadOnlyStatusAsRed) {
    myShowReadOnlyStatusAsRed = showReadOnlyStatusAsRed;
  }

  public String getUsagesString() {
    return myUsagesString;
  }

  public void setUsagesString(String usagesString) {
    myUsagesString = usagesString;
  }

  @Nullable("null means the targets node must not be visible")
  public String getTargetsNodeText() {
    return myTargetsNodeText;
  }

  public void setTargetsNodeText(String targetsNodeText) {
    myTargetsNodeText = targetsNodeText;
  }

  public boolean isShowCancelButton() {
    return myShowCancelButton;
  }

  public void setShowCancelButton(boolean showCancelButton) {
    myShowCancelButton = showCancelButton;
  }

  public String getNonCodeUsagesString() {
    return myNonCodeUsagesString;
  }

  public void setNonCodeUsagesString(String nonCodeUsagesString) {
    myNonCodeUsagesString = nonCodeUsagesString;
  }

  public String getCodeUsagesString() {
    return myCodeUsagesString;
  }

  public void setCodeUsagesString(String codeUsagesString) {
    myCodeUsagesString = codeUsagesString;
  }

  public boolean isOpenInNewTab() {
    return myOpenInNewTab;
  }

  public void setOpenInNewTab(boolean openInNewTab) {
    myOpenInNewTab = openInNewTab;
  }

  public boolean isCodeUsages() {
    return myCodeUsages;
  }

  public void setCodeUsages(final boolean codeUsages) {
    myCodeUsages = codeUsages;
  }

  public void addNotFoundAction(Action _action) {
    if (myNotFoundActions == null) myNotFoundActions = new ArrayList<Action>();
    myNotFoundActions.add(_action);
  }

  public List<Action> getNotFoundActions() {
    return myNotFoundActions;
  }

  public String getUsagesWord() {
    return myUsagesWord;
  }

  public void setUsagesWord(final String usagesWord) {
    myUsagesWord = usagesWord;
  }

  public String getTabName() {
    return myTabName;
  }

  public void setTabName(final String tabName) {
    myTabName = tabName;
  }

  public String getToolwindowTitle() {
    return myToolwindowTitle;
  }

  public void setToolwindowTitle(final String toolwindowTitle) {
    myToolwindowTitle = toolwindowTitle;
  }

  public boolean isDetachedMode() {
    return myDetachedMode;
  }

  public void setDetachedMode(boolean detachedMode) {
    myDetachedMode = detachedMode;
  }

  public void setDynamicUsagesString(String dynamicCodeUsagesString) {
    myDynamicCodeUsagesString = dynamicCodeUsagesString;
  }

  public String getDynamicCodeUsagesString() {
    return myDynamicCodeUsagesString;
  }

  public String getUsagesInGeneratedCodeString() {
    return myUsagesInGeneratedCodeString;
  }

  public void setUsagesInGeneratedCodeString(String usagesInGeneratedCodeString) {
    myUsagesInGeneratedCodeString = usagesInGeneratedCodeString;
  }

  public boolean isMergeDupLinesAvailable() {
    return myMergeDupLinesAvailable;
  }

  public void setMergeDupLinesAvailable(boolean mergeDupLinesAvailable) {
    myMergeDupLinesAvailable = mergeDupLinesAvailable;
  }
}

