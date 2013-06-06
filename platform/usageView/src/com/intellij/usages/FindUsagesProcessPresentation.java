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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class FindUsagesProcessPresentation {
  @NonNls
  public static final String NAME_WITH_MNEMONIC_KEY = "NameWithMnemonic";

  private List<Action> myNotFoundActions;
  private boolean myShowPanelIfOnlyOneUsage;
  private boolean myShowNotFoundMessage;
  private Factory<ProgressIndicator> myProgressIndicatorFactory;
  private Collection<PsiFile> myLargeFiles;
  private boolean myShowFindOptionsPrompt = true; // in the case of find in path, "find options" does not make sense

  public FindUsagesProcessPresentation() {
  }

  public void addNotFoundAction(@NotNull Action action) {
    if (myNotFoundActions == null) myNotFoundActions = new ArrayList<Action>();
    myNotFoundActions.add(action);
  }

  @NotNull
  public List<Action> getNotFoundActions() {
    return myNotFoundActions == null ? Collections.<Action>emptyList() : myNotFoundActions;
  }

  public boolean isShowNotFoundMessage() {
    return myShowNotFoundMessage;
  }

  public void setShowNotFoundMessage(final boolean showNotFoundMessage) {
    myShowNotFoundMessage = showNotFoundMessage;
  }

  public boolean isShowPanelIfOnlyOneUsage() {
    return myShowPanelIfOnlyOneUsage;
  }

  public void setShowPanelIfOnlyOneUsage(final boolean showPanelIfOnlyOneUsage) {
    myShowPanelIfOnlyOneUsage = showPanelIfOnlyOneUsage;
  }

  public Factory<ProgressIndicator> getProgressIndicatorFactory() {
    return myProgressIndicatorFactory;
  }

  public void setProgressIndicatorFactory(@NotNull Factory<ProgressIndicator> progressIndicatorFactory) {
    myProgressIndicatorFactory = progressIndicatorFactory;
  }

  public void setLargeFilesWereNotScanned(@NotNull Collection<PsiFile> largeFiles) {
    myLargeFiles = largeFiles;
  }

  @NotNull
  public Collection<PsiFile> getLargeFiles() {
    return myLargeFiles == null ? Collections.<PsiFile>emptyList() : myLargeFiles;
  }

  public boolean isShowFindOptionsPrompt() {
    return myShowFindOptionsPrompt;
  }

  public void setShowFindOptionsPrompt(boolean showFindOptionsPrompt) {
    myShowFindOptionsPrompt = showFindOptionsPrompt;
  }
}

