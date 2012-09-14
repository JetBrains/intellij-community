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
package com.intellij.openapi.diff.impl.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Workspace-wide settings for diff and merge tool UI customization.
 * Note that this is not a {@link PersistentStateComponent} itself. Instead both subclasses (separate for diff and merge) are.
 * This is done to let diff and merge tools have separate editor settings (for example, one may want to hide line numbers and enable soft
 * wraps for merge, because 3 columns occupy more space).
 *
 * @author Kirill Likhodedov
 */
public class DiffMergeSettings {

  private State myState = new State();

  public static class State {
    public boolean WHITESPACES = false;
    public boolean LINE_NUMBERS = true;
    public boolean INDENT_LINES = false;
    public boolean SOFT_WRAPS = false;
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public void setPreference(@NotNull DiffMergeEditorSetting setting, boolean state) {
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

  public boolean getPreference(@NotNull DiffMergeEditorSetting setting) {
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

  private void setShowLineNumbers(boolean state) {
    myState.LINE_NUMBERS = state;
  }

  private boolean isShowLineNumbers() {
    return myState.LINE_NUMBERS;
  }

  private void setShowWhiteSpaces(boolean state) {
    myState.WHITESPACES = state;
  }

  private boolean isShowWhitespaces() {
    return myState.WHITESPACES;
  }

  private void setShowIndentLines(boolean state) {
    myState.INDENT_LINES = state;
  }

  private boolean isShowIndentLines() {
    return myState.INDENT_LINES;
  }

  private void setUseSoftWraps(boolean state) {
    myState.SOFT_WRAPS = state;
  }

  private boolean isUseSoftWraps() {
    return myState.SOFT_WRAPS;
  }

}
