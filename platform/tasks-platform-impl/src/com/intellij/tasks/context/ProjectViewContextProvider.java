// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.context;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
final class ProjectViewContextProvider extends WorkingContextProvider {
  @NotNull
  @Override
  public String getId() {
    return "projectView";
  }

  @NotNull
  @Override
  public String getDescription() {
    return TaskBundle.message("project.view.state");
  }

  @Override
  public void saveContext(@NotNull Project project, @NotNull Element toElement) throws WriteExternalException {
    for (AbstractProjectViewPane pane : AbstractProjectViewPane.EP.getExtensions(project)) {
      Element paneElement = new Element(pane.getId());
      // When switching between branches, we don't want to persist presentations,
      // as they likely won't be applicable to another branch.
      // Besides, it causes glitches, as the tree is already loaded,
      // and the persist/restore presentation mechanism assumes an initially empty tree.
      pane.writeExternalWithoutPresentations(paneElement);
      toElement.addContent(paneElement);
    }
  }

  @Override
  public void loadContext(@NotNull Project project, @NotNull Element fromElement) throws InvalidDataException {
    for (AbstractProjectViewPane pane : AbstractProjectViewPane.EP.getExtensions(project)) {
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
  public void clearContext(@NotNull Project project) {
    for (AbstractProjectViewPane pane : AbstractProjectViewPane.EP.getExtensions(project)) {
      JTree tree = pane.getTree();
      if (tree != null) {
        TreeUtil.collapseAll(tree, 0);
      }
    }
  }
}
