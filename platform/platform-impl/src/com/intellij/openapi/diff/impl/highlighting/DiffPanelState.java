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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.ContentChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class DiffPanelState extends SimpleDiffPanelState {
  protected final int myDiffDividerPolygonsOffset;

  public DiffPanelState(ContentChangeListener changeListener,
                        Project project,
                        int diffDividerPolygonsOffset,
                        @NotNull Disposable parentDisposable) {
    super(project, changeListener, parentDisposable);
    myDiffDividerPolygonsOffset = diffDividerPolygonsOffset;
  }

  public void setContents(final DiffContent content1, final DiffContent content2) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myAppender1.setContent(content1);
        myAppender2.setContent(content2);
      }
    });
  }

  public DiffContent getContent1() {
    return myAppender1.getContent();
  }

  public DiffContent getContent2() {
    return myAppender2.getContent();
  }

  public void removeActions() {
    myAppender1.removeActions();
    myAppender2.removeActions();
  }

  public void drawOnDivider(final Graphics g, final JComponent component) {
  }

  public boolean isContentsEqual() {
    return Comparing.equal(myAppender1.getText(), myAppender2.getText());
  }
}

