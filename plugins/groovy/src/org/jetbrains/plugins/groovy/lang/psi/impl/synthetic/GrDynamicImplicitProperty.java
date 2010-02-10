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

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author ilyas
 */
public class GrDynamicImplicitProperty extends GrImplicitVariableImpl implements GrDynamicImplicitElement, PsiField {
  private final String myContainingClassName;
  private final Project myProject;
  private final PsiElement myNavigationalElement;

  public GrDynamicImplicitProperty(PsiManager manager, @NonNls String name, @NonNls @NotNull String type, String containingClassName,
                                   LightModifierList modifierList, PsiElement navigationalElement) {
    super(modifierList, manager, name, type, navigationalElement);
    myContainingClassName = containingClassName;
    myProject = manager.getProject();
    if (navigationalElement==null) {
      myNavigationalElement = this;
    }
    else {
      myNavigationalElement = navigationalElement;
    }
  }

  @Nullable
  public PsiClass getContainingClassElement() {
    final PsiClassType containingClassType = JavaPsiFacade.getInstance(getProject()).getElementFactory().
        createTypeByFQClassName(myContainingClassName, ProjectScope.getAllScope(getProject()));

    return containingClassType.resolve();
  }

  public String getContainingClassName() {
    return myContainingClassName;
  }

  @Override
  public PsiFile getContainingFile() {
    final PsiClass psiClass = getContainingClassElement();
    if (psiClass == null) return null;

    return psiClass.getContainingFile();
  }

  public String getPresentableText() {
    return getName();
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  @NotNull
  public SearchScope getUseScope() {
    return GlobalSearchScope.projectScope(myProject);
  }

  public void navigate(boolean requestFocus) {
    if (canNavigateToSource()) {
      super.navigate(requestFocus);
      return;
    }
    DynamicToolWindowWrapper.getInstance(myProject).getToolWindow().activate(new Runnable() {
      public void run() {
        DynamicToolWindowWrapper toolWindowWrapper = DynamicToolWindowWrapper.getInstance(myProject);
        final TreeTable treeTable = toolWindowWrapper.getTreeTable();
        final ListTreeTableModelOnColumns model = toolWindowWrapper.getTreeTableModel();

        Object root = model.getRoot();

        if (root == null || !(root instanceof DefaultMutableTreeNode)) return;

        DefaultMutableTreeNode treeRoot = ((DefaultMutableTreeNode) root);
        final PsiClass psiClass = getContainingClassElement();
        if (psiClass == null) return;

        final DefaultMutableTreeNode desiredNode;
        DPropertyElement dynamicProperty = null;
        PsiClass trueSuper = null;
        for (PsiClass aSuper : PsiUtil.iterateSupers(psiClass, true)) {
          dynamicProperty = DynamicManager.getInstance(myProject).findConcreteDynamicProperty(aSuper.getQualifiedName(), getName());

          if (dynamicProperty != null) {
            trueSuper = aSuper;
            break;
          }
        }

        if (trueSuper == null) return;

        final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DClassElement(myProject, trueSuper.getQualifiedName()));
        if (classNode == null) return;

        desiredNode = TreeUtil.findNodeWithObject(classNode, dynamicProperty);

        if (desiredNode == null) return;
        final TreePath path = TreeUtil.getPathFromRoot(desiredNode);

        treeTable.getTree().expandPath(path);
        treeTable.getTree().setSelectionPath(path);
        treeTable.getTree().fireTreeExpanded(path);

        ToolWindowManager.getInstance(myProject).getFocusManager().requestFocus(treeTable, true);
        treeTable.revalidate();
        treeTable.repaint();

      }
    }, true);
  }

  public boolean canNavigateToSource() {
    return myNavigationalElement != this;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationalElement;
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean isWritable() {
    return true;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }

  public boolean isValid() {
    return true;
  }

  public PsiClass getContainingClass() {
    return getContainingClassElement();
  }

    public PsiDocComment getDocComment() {
        return null;
    }

    public boolean isDeprecated() {
        return false;
    }
}