/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author ilyas
 */
public class GrDynamicImplicitPropertyImpl extends GrDynamicImplicitElement {
  public GrDynamicImplicitPropertyImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrDynamicImplicitPropertyImpl(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement referenceExpression) {
    super(manager, name, type, referenceExpression);
  }

  public void navigate(boolean requestFocus) {
    final Project myProject = myNameIdentifier.getProject();
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);

    window.activate(new Runnable() {
      public void run() {
        final TreeTable treeTable = DynamicToolWindowWrapper.getTreeTable(window, myProject);
        final ListTreeTableModelOnColumns model = DynamicToolWindowWrapper.getTreeTableModel(window, myProject);

        Object root = model.getRoot();

        if (root == null || !(root instanceof DefaultMutableTreeNode)) return;

        DefaultMutableTreeNode treeRoot = ((DefaultMutableTreeNode) root);
        if (!(myScope instanceof GrReferenceExpression)) return;

        final GrReferenceExpression refExpression = (GrReferenceExpression) myScope;
        final PsiClass psiClass = QuickfixUtil.findTargetClass(refExpression);
        final Module module = QuickfixUtil.getModuleByPsiFile(myScope.getContainingFile());

        if (psiClass == null) return;

        final DefaultMutableTreeNode desiredNode;
        DPropertyElement dynamicProperty = null;
        PsiClass trueSuper = null;
        for (PsiClass aSuper : GroovyUtils.iterateSupers(psiClass, true)) {
          dynamicProperty = DynamicManager.getInstance(myProject).findConcreteDynamicProperty(aSuper.getQualifiedName(), ((GrReferenceExpression) myScope).getName());

          if(dynamicProperty != null) {
            trueSuper = aSuper;
            break;
          }
        }

        if (trueSuper == null) return;

        final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DClassElement(module, trueSuper.getQualifiedName(), false));
        if (classNode == null) return;

        desiredNode = TreeUtil.findNodeWithObject(classNode, dynamicProperty);

        if (desiredNode == null) return;
        final TreePath path = TreeUtil.getPathFromRoot(desiredNode);

        treeTable.getTree().expandPath(path);
        treeTable.getTree().setSelectionPath(path);
        treeTable.getTree().fireTreeExpanded(path);

        treeTable.requestFocus();
        treeTable.revalidate();
        treeTable.repaint();
      }
    }, true);
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isPhysical() {
    return true;
  }
  
  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }

  public PsiFile getContainingFile() {
    return myScope.getContainingFile();
  }
}
