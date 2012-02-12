/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.impl.DiffSplitterI;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.ui.Splitter;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/12/11
 * Time: 12:05 PM
 */
public class HorizontalDiffSplitter extends Splitter implements DiffSplitterI {
  public HorizontalDiffSplitter(final JComponent first, final JComponent second) {
    super(true);
    setFirstComponent(first);
    setSecondComponent(second);
  }

  @Override
  public void redrawDiffs() {
  }

  @Override
  public VisibleAreaListener getVisibleAreaListener() {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void setProportion(float proportion) {
    super.setProportion(proportion);
    // I regret to put this hack here
    if (getFirstComponent() != null) {
      getFirstComponent().setVisible(proportion > 0.0001f);
    }
    if (getSecondComponent() != null) {
      getSecondComponent().setVisible(proportion < 0.9999f);
    }
  }
}
