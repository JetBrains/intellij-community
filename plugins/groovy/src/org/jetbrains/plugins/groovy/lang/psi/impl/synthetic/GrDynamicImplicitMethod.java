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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
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
public class GrDynamicImplicitMethod extends LightElement implements PsiMethod, GrDynamicImplicitElement {
  private final PsiManager myManager;

  private final GrMethod myMethod;
  private final String myContainingClassName;
  private final Project myProject;

  public GrDynamicImplicitMethod(PsiManager manager, GrMethod method, String containingClassName) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myManager = manager;

    myMethod = method;
    myProject = myManager.getProject();
    myContainingClassName = containingClassName;
  }

  public String[] getParameterTypes() {
    final PsiParameter[] psiParameters = getParameterList().getParameters();
    List<String> result = new ArrayList<String>();
    for (PsiParameter psiParameter : psiParameters) {
      result.add(psiParameter.getTypeElement().getType().getCanonicalText());
    }

    return ArrayUtil.toStringArray(result);
  }

  public String getContainingClassName() {
    return myContainingClassName;
  }

  @Nullable
  public PsiClass getContainingClassElement() {
    return JavaPsiFacade.getInstance(getProject()).findClass(myContainingClassName, ProjectScope.getAllScope(getProject()));
  }

  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  public PsiDocComment getDocComment() {
    return myMethod.getDocComment();
  }

  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return myMethod.setName(name);
  }

  @NotNull
  public String getName() {
    return myMethod.getName();
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  public boolean hasModifierProperty(@NotNull final String name) {
      final Ref<Boolean> res = new Ref<Boolean>();
      ApplicationManager.getApplication().runReadAction(new Runnable(){
          public void run() {
              res.set(myMethod.hasModifierProperty(name));
          }
      });
      return res.get();
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  public PsiType getReturnType() {
    final PsiType typeElement = myMethod.getDeclaredReturnType();
    if (typeElement == null) {
      return TypesUtil.getJavaLangObject(myMethod);
    }
    return typeElement;
  }

  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  public PsiCodeBlock getBody() {
    return myMethod.getBody();
  }

  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return myMethod.findSuperMethods();
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return myMethod.findSuperMethods(checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return myMethod.findSuperMethods(parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return myMethod.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return myMethod.findDeepestSuperMethod();
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return myMethod.findDeepestSuperMethods();
  }

  public String getText() {
    return myMethod.getText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    myMethod.accept(visitor);
  }

  public PsiElement copy() {
    return new GrDynamicImplicitMethod(myManager, (GrMethod) myMethod.copy(), myContainingClassName);
  }

  public boolean isValid() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    final PsiClass psiClass = getContainingClassElement();
    if (psiClass == null) return null;

    return psiClass.getContainingFile();
  }

  @Nullable
  public PsiClass getContainingClass() {
    final Ref<PsiClass> aclass = new Ref<PsiClass>(null);

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(myProject).createTypeElement(myContainingClassName);
          if (typeElement == null) return;

          final PsiType type = typeElement.getType();
          if (!(type instanceof PsiClassType)) return;

          aclass.set(((PsiClassType) type).resolve());
        } catch (IncorrectOperationException e) {
        }
      }
    });

    return aclass.get();
  }

  public String toString() {
    return "DynamicMethod:" + getName();
  }

  @NotNull
  public SearchScope getUseScope() {
    return GlobalSearchScope.projectScope(myProject);
  }

  public void navigate(boolean requestFocus) {

    DynamicToolWindowWrapper.getInstance(myProject).getToolWindow().activate(new Runnable() {
      public void run() {
        DynamicToolWindowWrapper toolWindowWrapper = DynamicToolWindowWrapper.getInstance(myProject);
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

        final GrParameter[] parameters = myMethod.getParameterList().getParameters();

        List<String> parameterTypes = new ArrayList<String>();
        for (GrParameter parameter : parameters) {
          final String type = parameter.getTypeElementGroovy().getType().getCanonicalText();
          parameterTypes.add(type);
        }

        for (PsiClass aSuper : PsiUtil.iterateSupers(psiClass, true)) {
          methodElement = DynamicManager.getInstance(myProject).findConcreteDynamicMethod(aSuper.getQualifiedName(), getName(), ArrayUtil.toStringArray(parameterTypes));

          if (methodElement != null) {
            trueClass = aSuper;
            break;
          }
        }

        if (trueClass == null) return;
        final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DClassElement(myProject, trueClass.getQualifiedName()));

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

  public String getPresentableText() {
    return getName();
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.METHOD;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  public GrMethod getMethod() {
    return myMethod;
  }
  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  public PsiElement getContext() {
    return myMethod;
  }
}
