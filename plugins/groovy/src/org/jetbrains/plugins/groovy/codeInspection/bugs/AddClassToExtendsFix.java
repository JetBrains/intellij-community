// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class AddClassToExtendsFix extends GroovyFix {
  private final GrTypeDefinition myPsiClass;
  private final String myInterfaceName;

  public AddClassToExtendsFix(GrTypeDefinition psiClass, String interfaceName) {
    myPsiClass = psiClass;
    myInterfaceName = interfaceName;
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {

    GrReferenceList list;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    final PsiClass comparable =
      JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, myPsiClass.getResolveScope());
    final boolean addTypeParam = comparable != null && comparable.getTypeParameters().length == 1;

    if (!InheritanceUtil.isInheritor(myPsiClass, CommonClassNames.JAVA_LANG_COMPARABLE)) {
      if (myPsiClass.isInterface()) {
        list = myPsiClass.getExtendsClause();
        if (list == null) {
          list = factory.createExtendsClause();

          PsiElement anchor = myPsiClass.getImplementsClause();
          if (anchor == null) {
            anchor = myPsiClass.getBody();
          }
          if (anchor == null) return;
          list = (GrReferenceList)myPsiClass.addBefore(list, anchor);
          myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
          myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
        }
      }
      else {
        list = myPsiClass.getImplementsClause();
        if (list == null) {
          list = factory.createImplementsClause();
          PsiElement anchor = myPsiClass.getBody();
          if (anchor == null) return;
          list = (GrReferenceList)myPsiClass.addBefore(list, anchor);
          myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", list.getNode());
          myPsiClass.getNode().addLeaf(TokenType.WHITE_SPACE, " ", anchor.getNode());
        }
      }


      final GrCodeReferenceElement _ref =
        factory.createReferenceElementFromText(myInterfaceName + (addTypeParam ? "<" + AddMethodFix.generateTypeText(myPsiClass) + ">" : ""));
      final GrCodeReferenceElement ref = (GrCodeReferenceElement)list.add(_ref);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
    }
    if (comparable != null && !myPsiClass.isInterface()) {
      final PsiMethod baseMethod = comparable.getMethods()[0];
      OverrideImplementUtil.overrideOrImplement(myPsiClass, baseMethod);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return GroovyInspectionBundle.message("implement.class", myInterfaceName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Implement";
  }
}
