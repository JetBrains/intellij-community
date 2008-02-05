/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPContainingClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.tree.DPPropertyNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.tree.DPClassNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author ilyas
 */
public class GrImplicitVariableImpl extends LightVariableBase implements GrImplicitVariable {

  public GrImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrImplicitVariableImpl(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement referenceExpression) {
    this(manager, null, manager.getElementFactory().createTypeByFQClassName(type, manager.getProject().getAllScope()), false, referenceExpression);
    myNameIdentifier = new GrLightIdentifier(myManager, name);
  }


  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitImplicitVariable(this);
  }

  public ItemPresentation getPresentation() {
    return this;
  }


  public String toString() {
    return "Specific implicit variable";
  }

  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public String getPresentableText() {
    return null;
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  public boolean isPhysical() {
    return true;
  }

  @NotNull
  public SearchScope getUseScope() {
    return myScope.getProject().getAllScope();
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  protected class GrLightIdentifier extends LightIdentifier {
    private String myTextInternal;

    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
      myTextInternal = name;
    }

    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
      myTextInternal = newElement.getText();
      return newElement;
    }

    public String getText() {
      return myTextInternal;
    }

    public PsiElement copy() {
      return new LightIdentifier(getManager(), myTextInternal);
    }
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
        final PsiType type = refExpression.getQualifierExpression().getType();

        if (type == null) return;

        final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DPClassNode(new DPContainingClassElement(type.getCanonicalText())));
        if (classNode == null) return;
        
        final DefaultMutableTreeNode desiredNode = TreeUtil.findNodeWithObject(classNode, new DPPropertyNode(new DPPropertyElement(myNameIdentifier.getText())));
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

//  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
//    return super.setName(name);    //To change body of overridden methods use File | Settings | File Templates.
//  }
}
