/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

/**
* @author irengrig
*/
public abstract class BaseLineStatusRangeAction extends AnAction implements DumbAware {
  protected final LineStatusTracker myLineStatusTracker;
  protected final Range myRange;

  BaseLineStatusRangeAction(final String text, final Icon icon, final LineStatusTracker lineStatusTracker, final Range range) {
    super(text, null, icon);
    myLineStatusTracker = lineStatusTracker;
    myRange = range;
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(!myLineStatusTracker.isSilentMode() && myLineStatusTracker.isValid() && isEnabled());
  }

  public abstract boolean isEnabled();
}
