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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
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
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {

    GrReferenceList list;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    final PsiClass comparable =
      JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, myPsiClass.getResolveScope());
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    boolean addTypeParam = false;
    if (comparable != null) {
      final PsiTypeParameter[] typeParameters = comparable.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        final PsiTypeParameter[] classParams = myPsiClass.getTypeParameters();
        PsiSubstitutor innerSubstitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter classParam : classParams) {
          innerSubstitutor = innerSubstitutor.put(classParam, elementFactory.createType(classParam));
        }
        substitutor = substitutor.put(typeParameters[0], elementFactory.createType(myPsiClass, innerSubstitutor));
        addTypeParam = true;
      }
    }

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
}
