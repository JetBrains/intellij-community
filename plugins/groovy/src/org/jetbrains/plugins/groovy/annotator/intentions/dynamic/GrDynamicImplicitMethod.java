/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrDynamicImplicitElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.02.2008
 */
public class GrDynamicImplicitMethod extends GrLightMethodBuilder implements GrDynamicImplicitElement {
  private static final Logger LOG = Logger.getInstance(GrDynamicImplicitMethod.class);

  private final String myContainingClassName;
  private final List<ParamInfo> myParamInfos;
  private final String myReturnType;

  public GrDynamicImplicitMethod(PsiManager manager,
                                 String name,
                                 String containingClassName,
                                 boolean isStatic,
                                 List<ParamInfo> paramInfos,
                                 String returnType) {
    super(manager, name);
    myContainingClassName = containingClassName;
    myParamInfos = paramInfos;
    setOriginInfo("dynamic method");

    if (isStatic) {
      addModifier(PsiModifier.STATIC);
    }

    for (ParamInfo pair : paramInfos) {
      addParameter(pair.name, pair.type, false);
    }

    setReturnType(returnType, getResolveScope());
    myReturnType = returnType;
  }

  @Override
  public String getContainingClassName() {
    return myContainingClassName;
  }

  @Override
  @Nullable
  public PsiClass getContainingClassElement() {
    return JavaPsiFacade.getInstance(getProject()).findClass(myContainingClassName, ProjectScope.getAllScope(getProject()));
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    String[] argumentsTypes = QuickfixUtil.getArgumentsTypes(myParamInfos);
    DynamicManager.getInstance(getProject()).replaceDynamicMethodName(getContainingClassName(), getName(), name, argumentsTypes);

    return super.setName(name);
  }

  @Override
  public GrDynamicImplicitMethod copy() {
    return new GrDynamicImplicitMethod(myManager, getName(), getContainingClassName(), hasModifierProperty(PsiModifier.STATIC), ContainerUtil.newArrayList(myParamInfos), myReturnType);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    final PsiClass psiClass = getContainingClassElement();
    if (psiClass == null) return null;

    return psiClass.getContainingFile();
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        try {
          final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(myContainingClassName);
          if (typeElement == null) return null;

          final PsiType type = typeElement.getType();
          if (!(type instanceof PsiClassType)) return null;

          return ((PsiClassType)type).resolve();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return null;
        }
      }
    });
  }

  public String toString() {
    return "DynamicMethod:" + getName();
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return GlobalSearchScope.projectScope(getProject());
  }

  @Override
  public void navigate(boolean requestFocus) {

    DynamicToolWindowWrapper.getInstance(getProject()).getToolWindow().activate(() -> {
      DynamicToolWindowWrapper toolWindowWrapper = DynamicToolWindowWrapper.getInstance(getProject());
      final TreeTable treeTable = toolWindowWrapper.getTreeTable();
      final ListTreeTableModelOnColumns model = toolWindowWrapper.getTreeTableModel();

      Object root = model.getRoot();

      if (root == null || !(root instanceof DefaultMutableTreeNode)) return;

      DefaultMutableTreeNode treeRoot = ((DefaultMutableTreeNode) root);
      DefaultMutableTreeNode desiredNode;

      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiClassType fqClassName = facade.getElementFactory().createTypeByFQClassName(myContainingClassName, ProjectScope.getAllScope(getProject()));
      final PsiClass psiClass = fqClassName.resolve();
      if (psiClass == null) return;

      PsiClass trueClass = null;
      DMethodElement methodElement = null;

      final GrParameter[] parameters = getParameters();

      List<String> parameterTypes = new ArrayList<>();
      for (GrParameter parameter : parameters) {
        final String type = parameter.getType().getCanonicalText();
        parameterTypes.add(type);
      }

      for (PsiClass aSuper : PsiUtil.iterateSupers(psiClass, true)) {
        methodElement = DynamicManager.getInstance(getProject()).findConcreteDynamicMethod(aSuper.getQualifiedName(), getName(), ArrayUtil.toStringArray(parameterTypes));

        if (methodElement != null) {
          trueClass = aSuper;
          break;
        }
      }

      if (trueClass == null) return;
      final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DClassElement(getProject(), trueClass.getQualifiedName()));

      if (classNode == null) return;
      desiredNode = TreeUtil.findNodeWithObject(classNode, methodElement);

      if (desiredNode == null) return;
      final TreePath path = TreeUtil.getPathFromRoot(desiredNode);

      treeTable.getTree().expandPath(path);
      treeTable.getTree().setSelectionPath(path);
      treeTable.getTree().fireTreeExpanded(path);

//        ToolWindowManager.getInstance(myProject).getFocusManager().requestFocus(treeTable, true);
      treeTable.revalidate();
      treeTable.repaint();
    }, true);
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public String getPresentableText() {
    return getName();
  }

  @Override
  @Nullable
  public String getLocationString() {
    return null;
  }

  @Override
  @Nullable
  public Icon getIcon(boolean open) {
    return JetgroovyIcons.Groovy.Method;
  }
}
