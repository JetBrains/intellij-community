/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.context;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class ProjectViewContextProvider extends WorkingContextProvider {

  private final AbstractProjectViewPane[] myPanes;

  public ProjectViewContextProvider(Project project) {
    myPanes = Extensions.getExtensions(AbstractProjectViewPane.EP_NAME, project);
  }

  @NotNull
  @Override
  public String getId() {
    return "projectView";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Project view state";
  }

  public void saveContext(Element toElement) throws WriteExternalException {
    for (AbstractProjectViewPane pane : myPanes) {
      Element paneElement = new Element(pane.getId());
      pane.writeExternal(paneElement);
      toElement.addContent(paneElement);
    }
  }

  public void loadContext(Element fromElement) throws InvalidDataException {
    for (AbstractProjectViewPane pane : myPanes) {
      Element paneElement = fromElement.getChild(pane.getId());
      if (paneElement != null) {
        pane.readExternal(paneElement);
        if (pane.getTree() != null) {
          pane.restoreExpandedPaths();
        }
      }
    }
  }

  public void clearContext() {
    for (AbstractProjectViewPane pane : myPanes) {
      JTree tree = pane.getTree();
      if (tree != null) {
        TreeUtil.collapseAll(tree, 0);
      }
    }
  }
}
