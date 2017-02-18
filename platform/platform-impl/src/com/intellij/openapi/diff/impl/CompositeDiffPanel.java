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
import com.intellij.openapi.util.Pair;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
  private final Map<String, Pair<DiffViewer, Content>> myMap;

  public CompositeDiffPanel(Project project, final DiscloseMultiRequest request, final Window window, @NotNull Disposable parentDisposable) {
    myRequest = request;
    myWindow = window;
    myParentDisposable = parentDisposable;
    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Diff", "Diff", "Diff", parentDisposable);
    myUi.getComponent().setBorder(null);
    myUi.getOptions().setMinimizeActionEnabled(false);
    //myUi.getOptions().setTopToolbar()
    myMap = new HashMap<>();
  }

  @Override
  public boolean canShowRequest(DiffRequest request) {
    return MultiLevelDiffTool.canShowRequest(request);
  }

  @Override
  public void setDiffRequest(DiffRequest request) {
    final Map<String, DiffRequest> requestMap = myRequest.discloseRequest(request);

    HashMap<String, Pair<DiffViewer, Content>> mapCopy = new HashMap<>(myMap);
    myMap.clear();

    for (Map.Entry<String, DiffRequest> entry : requestMap.entrySet()) {
      final String key = entry.getKey();
      final DiffRequest diffRequest = entry.getValue();
      diffRequest.getGenericData().put(PlatformDataKeys.COMPOSITE_DIFF_VIEWER.getName(), this);
      final Pair<DiffViewer, Content> pair = mapCopy.get(key);
      DiffViewer viewer = pair != null ? pair.first : null;
      if (viewer != null && viewer.acceptsType(diffRequest.getType()) && viewer.canShowRequest(diffRequest)) {
        viewer.setDiffRequest(diffRequest);
        myMap.put(key, pair);
        mapCopy.remove(key);
      } else {
        final DiffViewer newViewer = myRequest.viewerForRequest(myWindow, myParentDisposable, key, diffRequest);
        if (newViewer == null) continue;
        final Content content = myUi.createContent(key, newViewer.getComponent(), key, null, newViewer.getPreferredFocusedComponent());
        content.setCloseable(false);
        content.setPinned(true);
        content.setDisposer(myParentDisposable);
        myUi.addContent(content);
        myMap.put(key, Pair.create(newViewer, content));
        if (pair != null) myUi.removeContent(pair.second, false);
      }
    }

    if (myMap.isEmpty()) {
      final ErrorDiffViewer errorDiffViewer = new ErrorDiffViewer(myWindow, request);
      final Content content = myUi.createContent(FICTIVE_KEY, errorDiffViewer.getComponent(), FICTIVE_KEY, null,
                                                 errorDiffViewer.getPreferredFocusedComponent());
      content.setCloseable(false);
      content.setPinned(true);
      content.setDisposer(myParentDisposable);
      myUi.addContent(content);
      myMap.put(FICTIVE_KEY, Pair.<DiffViewer, Content>create(errorDiffViewer, content));
    }

    for (Pair<DiffViewer, Content> pair : mapCopy.values()) {
      myUi.removeContent(pair.second, false);
      if (pair.first instanceof Disposable) Disposer.dispose((Disposable)pair.first);
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
