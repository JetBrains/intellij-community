// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import static com.intellij.psi.util.PointersKt.createSmartPointer;

public class AddClassToExtendsFix extends GroovyFix {
  private final SmartPsiElementPointer<GrTypeDefinition> myPsiClassPointer;
  private final String myInterfaceName;

  public AddClassToExtendsFix(@NotNull GrTypeDefinition psiClass, @NotNull String interfaceName) {
    myPsiClassPointer = createSmartPointer(psiClass);
    myInterfaceName = interfaceName;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    GrTypeDefinition element = myPsiClassPointer.getElement();
    if (element == null) {
      return null;
    }
    GrTypeDefinition copy = PsiTreeUtil.findSameElementInCopy(element, target);
    return new AddClassToExtendsFix(copy, myInterfaceName);
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final GrTypeDefinition psiClass = myPsiClassPointer.getElement();
    if (psiClass == null) return;

    GrReferenceList list;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    final PsiClass comparable =
      JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, psiClass.getResolveScope());
    final boolean addTypeParam = comparable != null && comparable.getTypeParameters().length == 1;

    if (!InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_COMPARABLE)) {
      if (psiClass.isInterface()) {
        list = psiClass.getExtendsClause();
        if (list == null) {
          list = factory.createExtendsClause();

          PsiElement anchor = psiClass.getImplementsClause();
          if (anchor == null) {
            anchor = psiClass.getBody();
          }
          if (anchor == null) return;
          list = (GrReferenceList)psiClass.addBefore(list, anchor);
          psiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
          psiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
        }
      }
      else {
        list = psiClass.getImplementsClause();
        if (list == null) {
          list = factory.createImplementsClause();
          PsiElement anchor = psiClass.getBody();
          if (anchor == null) return;
          list = (GrReferenceList)psiClass.addBefore(list, anchor);
          psiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
          psiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
        }
      }


      final GrCodeReferenceElement _ref =
        factory.createCodeReference(myInterfaceName + (addTypeParam ? "<" + AddMethodFix.generateTypeText(psiClass) + ">" : ""));
      final GrCodeReferenceElement ref = (GrCodeReferenceElement)list.add(_ref);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
    }
    if (comparable != null && !psiClass.isInterface()) {
      final PsiMethod baseMethod = comparable.getMethods()[0];
      OverrideImplementUtil.overrideOrImplement(psiClass, baseMethod);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("implement.class", myInterfaceName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("add.class.to.extends.family.name");
  }
}
