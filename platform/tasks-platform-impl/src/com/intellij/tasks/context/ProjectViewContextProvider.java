// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.context;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
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
    myPanes = AbstractProjectViewPane.EP_NAME.getExtensions(project);
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

  @Override
  public void saveContext(Element toElement) throws WriteExternalException {
    for (AbstractProjectViewPane pane : myPanes) {
      Element paneElement = new Element(pane.getId());
      pane.writeExternal(paneElement);
      toElement.addContent(paneElement);
    }
  }

  @Override
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

  @Override
  public void clearContext() {
    for (AbstractProjectViewPane pane : myPanes) {
      JTree tree = pane.getTree();
      if (tree != null) {
        TreeUtil.collapseAll(tree, 0);
      }
    }
  }
}
