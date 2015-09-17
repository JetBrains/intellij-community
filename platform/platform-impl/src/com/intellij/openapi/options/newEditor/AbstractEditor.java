/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * @author Sergey.Malenkov
 */
abstract class AbstractEditor extends JPanel implements Disposable {
  volatile boolean myDisposed;

  AbstractEditor(Disposable parent) {
    super(new BorderLayout());
    Disposer.register(parent, this);
  }

  @Override
  public final void dispose() {
    if (!myDisposed) {
      myDisposed = true;
      disposeOnce();
    }
  }

  abstract void disposeOnce();

  abstract Action getApplyAction();

  abstract Action getResetAction();

  abstract String getHelpTopic();

  abstract boolean apply();

  boolean cancel() {
    return true;
  }

  abstract JComponent getPreferredFocusedComponent();
}
