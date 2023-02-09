// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
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
import java.util.Collection;
import java.util.List;

public class GrDynamicImplicitMethod extends GrLightMethodBuilder implements GrDynamicImplicitElement {
  private static final Logger LOG = Logger.getInstance(GrDynamicImplicitMethod.class);

  private final String myContainingClassName;
  private final List<? extends ParamInfo> myParamInfos;
  private final String myReturnType;

  public GrDynamicImplicitMethod(PsiManager manager,
                                 String name,
                                 String containingClassName,
                                 boolean isStatic,
                                 List<? extends ParamInfo> paramInfos,
                                 String returnType) {
    super(manager, name);
    myContainingClassName = containingClassName;
    myParamInfos = paramInfos;
    setOriginInfo("dynamic method");

    if (isStatic) {
      addModifier(PsiModifier.STATIC);
    }

    for (ParamInfo pair : paramInfos) {
      addParameter(pair.name, pair.type);
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
    return new GrDynamicImplicitMethod(myManager, getName(), getContainingClassName(), hasModifierProperty(PsiModifier.STATIC),
                                       new ArrayList<>((Collection<? extends ParamInfo>)myParamInfos), myReturnType);
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
    return ReadAction.compute(() -> {
      try {
        final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(myContainingClassName);

        return typeElement.getType() instanceof PsiClassType type ? type.resolve() : null;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    });
  }

  @Override
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

      if (!(root instanceof DefaultMutableTreeNode treeRoot)) return;

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
        methodElement = DynamicManager.getInstance(getProject()).findConcreteDynamicMethod(aSuper.getQualifiedName(), getName(),
                                                                                           ArrayUtilRt.toStringArray(parameterTypes));

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
  public Icon getIcon(boolean open) {
    return JetgroovyIcons.Groovy.Method;
  }
}
