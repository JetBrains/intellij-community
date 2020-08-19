// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;

/**
* @author peter
*/
public class TestsTopLevelDirectoryNode extends TopLevelDirectoryNode {
  private final Icon myMethodIcon;

  public TestsTopLevelDirectoryNode(Module module,
                                    PsiDirectory testDir,
                                    ViewSettings viewSettings,
                                    final @Nls String title,
                                    final Icon icon, final Icon methodIcon) {
    super(module, testDir, viewSettings, title, icon, TESTS_FOLDER);
    myMethodIcon = methodIcon;
  }

  @Override
  protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
    return new TestClassNode(getModule(), typeDefinition, getSettings(), myMethodIcon);
  }
}
