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
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.tree.DPPopertyNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.toolPanel.DynamicToolWindowUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author ilyas
 */
public class GrImplicitVariable extends LightVariableBase implements ItemPresentation, NavigationItem, ImplicitVariable {

  public GrImplicitVariable(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrImplicitVariable(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement scope) {
    this(manager, null, manager.getElementFactory().createTypeByFQClassName(type, manager.getProject().getAllScope()), false, scope);
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

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  protected class GrLightIdentifier extends LightIdentifier {
    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
    }
  }

  public void navigate(boolean requestFocus) {
    final Project myProject = myNameIdentifier.getProject();
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowUtil.DYNAMIC_TOOLWINDOW_ID);
    DynamicToolWindowWrapper dynamicToolWindowWrapper = new DynamicToolWindowWrapper(myProject, window);
    dynamicToolWindowWrapper.setupToolWindow(window);

    final ListTreeTableModelOnColumns model = dynamicToolWindowWrapper.getTreeTableModel();
    final TreeTable treeTable = dynamicToolWindowWrapper.getTreeTable();

    if (model != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          window.activate(new Runnable() {
            public void run() {
              Object root = model.getRoot();

              if (root == null || !(root instanceof DefaultMutableTreeNode)) return;

              DefaultMutableTreeNode treeRoot = ((DefaultMutableTreeNode) root);

              final DefaultMutableTreeNode desiredNode = TreeUtil.findNodeWithObject(treeRoot, new DPPopertyNode(new DPPropertyElement(myNameIdentifier.getText())));
//          int rootNum = treeModel.getIndexOfChild(root, desiredNode);
              final TreePath path = TreeUtil.getPathFromRoot(desiredNode);

              treeTable.getTree().setSelectionPath(path);
//          final int rowToSelect = treeTable.getTree().getRowForPath(path);

//          ToolWindowManager.getInstance(myProject).requestFocus(new ActionCallback.Runnable(){
//            public ActionCallback run() {
//              window.show(null);
//              return new ActionCallback.Done();
//            }
//          }, true);
            }
          }, true);
        }
      });

    }
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public boolean canNavigate() {
    return true;
  }
}
