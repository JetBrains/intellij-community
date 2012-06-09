/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;

/**
 * Workspace-wide settings for merge tool UI customization.
 *
 * @author Kirill Likhodedov
 */
@State(name = "MergeToolSettings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class MergeToolSettings implements PersistentStateComponent<MergeToolSettings.State>  {

  private State myState = new State();

  public static class State {
    public boolean WHITESPACES = false;
    public boolean LINE_NUMBERS = true;
    public boolean INDENT_LINES = false;
    public boolean SOFT_WRAPS = false;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public void setPreference(@NotNull MergeToolEditorSetting setting, boolean state) {
    switch (setting) {
      case WHITESPACES:
        setShowWhiteSpaces(state);
        break;
      case LINE_NUMBERS:
        setShowLineNumbers(state);
        break;
      case INDENT_LINES:
        setShowIndentLines(state);
        break;
      case SOFT_WRAPS:
        setUseSoftWraps(state);
        break;
    }
  }

  public boolean getPreference(@NotNull MergeToolEditorSetting setting) {
    switch (setting) {
      case WHITESPACES:
        return isShowWhitespaces();
      case LINE_NUMBERS:
        return isShowLineNumbers();
      case INDENT_LINES:
        return isShowIndentLines();
      case SOFT_WRAPS:
        return isUseSoftWraps();
    }
    return false;
  }

  public void setShowLineNumbers(boolean state) {
    myState.LINE_NUMBERS = state;
  }

  public boolean isShowLineNumbers() {
    return myState.LINE_NUMBERS;
  }

  public void setShowWhiteSpaces(boolean state) {
    myState.WHITESPACES = state;
  }

  public boolean isShowWhitespaces() {
    return myState.WHITESPACES;
  }

  public void setShowIndentLines(boolean state) {
    myState.INDENT_LINES = state;
  }

  public boolean isShowIndentLines() {
    return myState.INDENT_LINES;
  }

  public void setUseSoftWraps(boolean state) {
    myState.SOFT_WRAPS = state;
  }

  public boolean isUseSoftWraps() {
    return myState.SOFT_WRAPS;
  }

}
