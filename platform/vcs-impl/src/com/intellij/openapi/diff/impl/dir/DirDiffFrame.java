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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffFrame extends FrameWrapper {
  private DirDiffPanel myPanel;

  public DirDiffFrame(Project project, DirDiffTableModel model, DirDiffSettings settings) {
    super(project, "DirDiffDialog");
    setSize(new Dimension(800, 600));
    myPanel = new DirDiffPanel(model, new DirDiffWindow(this), settings);
    Disposer.register(this, myPanel);
    setComponent(myPanel.getPanel());
    setProject(project);
  }


  @Override
  protected void loadFrameState() {
    super.loadFrameState();
    myPanel.setupSplitter();
  }
}
