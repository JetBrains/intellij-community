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
package com.intellij.openapi.diff.impl;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.DiffViewerType;
import com.intellij.openapi.diff.impl.external.DiscloseMultiRequest;
import com.intellij.openapi.diff.impl.external.MultiLevelDiffTool;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/13/12
 * Time: 1:59 PM
 */
public class CompositeDiffPanel implements DiffViewer {
  private final static String FICTIVE_KEY = "FICTIVE_KEY";
  private final static int ourBadHackMagicContentsNumber = 101;
  private final RunnerLayoutUi myUi;
  private final DiscloseMultiRequest myRequest;
  private final Window myWindow;
  private final Disposable myParentDisposable;
  private final Map<String, DiffViewer> myMap;

  public CompositeDiffPanel(Project project, final DiscloseMultiRequest request, final Window window, @NotNull Disposable parentDisposable) {
    myRequest = request;
    myWindow = window;
    myParentDisposable = parentDisposable;
    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Diff", "Diff", "Diff", parentDisposable);
    myUi.getComponent().setBorder(null);
    myUi.getOptions().setMinimizeActionEnabled(false);
    //myUi.getOptions().setTopToolbar()
    myMap = new HashMap<String, DiffViewer>();
  }

  @Override
  public boolean canShowRequest(DiffRequest request) {
    return MultiLevelDiffTool.canShowRequest(request);
  }

  @Override
  public void setDiffRequest(DiffRequest request) {
    final Map<String, DiffRequest> requestMap = myRequest.discloseRequest(request);
    final HashMap<String, DiffViewer> copy = new HashMap<String, DiffViewer>(myMap);

    for (Map.Entry<String, DiffRequest> entry : requestMap.entrySet()) {
      final String key = entry.getKey();
      final DiffRequest diffRequest = entry.getValue();
      diffRequest.getGenericData().put(PlatformDataKeys.COMPOSITE_DIFF_VIEWER.getName(), this);
      final DiffViewer viewer = copy.remove(key);
      if (viewer != null && viewer.acceptsType(diffRequest.getType()) && viewer.canShowRequest(diffRequest)) {
        viewer.setDiffRequest(diffRequest);
      } else {
        if (viewer != null) {
          removeTab(myUi.getContentManager().getContents(), key);
        }
        final DiffViewer newViewer = myRequest.viewerForRequest(myWindow, myParentDisposable, key, diffRequest);
        if (newViewer == null) continue;
        myMap.put(key, newViewer);
        final Content content = myUi.createContent(key, newViewer.getComponent(), key, null, newViewer.getPreferredFocusedComponent());
        content.setCloseable(false);
        content.setPinned(true);
        Disposer.register(myParentDisposable, new Disposable() {
          @Override
          public void dispose() {
            myMap.remove(key);
            myUi.removeContent(content, true);
          }
        });
        myUi.addContent(content);
      }
    }
    final Content[] contents = myUi.getContentManager().getContents();
    for (String s : copy.keySet()) {
      removeTab(contents, s);
    }

    if (myMap.isEmpty()) {
      final EmptyDiffViewer emptyDiffViewer = new EmptyDiffViewer();
      emptyDiffViewer.setDiffRequest(request);
      myMap.put(FICTIVE_KEY, emptyDiffViewer);
      final Content content = myUi.createContent(FICTIVE_KEY, emptyDiffViewer.getComponent(), FICTIVE_KEY, null,
                                                 emptyDiffViewer.getPreferredFocusedComponent());
      content.setCloseable(false);
      content.setPinned(true);
      content.setDisposer(myParentDisposable);
      myUi.addContent(content);
    }
  }

  private void removeTab(Content[] contents, String s) {
    myMap.remove(s);
    for (Content content : contents) {
      if (s.equals(content.getDisplayName())) {
        myUi.getContentManager().removeContent(content, false);
        break;
      }
    }
  }

  @Override
  public JComponent getComponent() {
    return myUi.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    final Content[] contents = myUi.getContents();
    if (contents == null || contents.length == 0) return null;
    return contents[0].getPreferredFocusableComponent();
  }

  @Override
  public int getContentsNumber() {
    return ourBadHackMagicContentsNumber;
  }

  @Override
  public boolean acceptsType(DiffViewerType type) {
    return DiffViewerType.multiLayer.equals(type) || DiffViewerType.contents.equals(type) || DiffViewerType.merge.equals(type);
  }
}
