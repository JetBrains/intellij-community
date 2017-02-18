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
package org.jetbrains.plugins.groovy.actions.generate.constructors;

import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2008
 */
public class GroovyGenerateConstructorHandler extends GenerateConstructorHandler {
  private static final Logger LOG = Logger.getInstance(GroovyGenerateConstructorHandler.class);

  private static final String DEF_PSEUDO_ANNO = "_____intellij_idea_rulez_def_";

  @Override
  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    final ClassMember[] classMembers = chooseOriginalMembersImpl(aClass, project);
    if (classMembers == null) return null;

    List<ClassMember> res = new ArrayList<>();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    for (ClassMember classMember : classMembers) {
      if (classMember instanceof PsiMethodMember) {
        final PsiMethod method = ((PsiMethodMember)classMember).getElement();

        PsiMethod copy = factory.createMethodFromText(GroovyToJavaGenerator.generateMethodStub(method), method);
        if (method instanceof GrMethod) {
          GrParameter[] parameters = ((GrMethod)method).getParameterList().getParameters();
          PsiParameter[] copyParameters = copy.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getTypeElementGroovy() == null) {
              copyParameters[i].setName(DEF_PSEUDO_ANNO + parameters[i].getName());
            }
          }
        }

        res.add(new PsiMethodMember(copy));
      }
      else if (classMember instanceof PsiFieldMember) {
        final PsiField field = ((PsiFieldMember)classMember).getElement();

        String prefix = field instanceof GrField && ((GrField)field).getTypeElementGroovy() == null ? DEF_PSEUDO_ANNO : "";
        res.add(
          new PsiFieldMember(factory.createFieldFromText(field.getType().getCanonicalText() + " " + prefix + field.getName(), aClass)));
      }
    }

    return res.toArray(new ClassMember[res.size()]);
  }

  @Nullable
  protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
    return super.chooseOriginalMembers(aClass, project);
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members)
    throws IncorrectOperationException {
    final List<? extends GenerationInfo> list = super.generateMemberPrototypes(aClass, members);

    List<PsiGenerationInfo<GrMethod>> grConstructors = new ArrayList<>();

    final Project project = aClass.getProject();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    for (GenerationInfo generationInfo : list) {
      final PsiMember constructorMember = generationInfo.getPsiMember();
      assert constructorMember instanceof PsiMethod;
      final PsiMethod constructor = (PsiMethod)constructorMember;

      final PsiCodeBlock block = constructor.getBody();
      assert block != null;

      final String constructorName = aClass.getName();
      final String body = StringUtil.replace(StringUtil.replace(block.getText(), DEF_PSEUDO_ANNO, ""), ";", "");
      final PsiParameterList list1 = constructor.getParameterList();

      List<String> parametersNames = new ArrayList<>();
      List<String> parametersTypes = new ArrayList<>();
      for (PsiParameter parameter : list1.getParameters()) {
        final String fullName = parameter.getName();
        parametersNames.add(StringUtil.trimStart(fullName, DEF_PSEUDO_ANNO));
        parametersTypes.add(fullName.startsWith(DEF_PSEUDO_ANNO) ? null : parameter.getType().getCanonicalText());
      }

      final String[] paramNames = ArrayUtil.toStringArray(parametersNames);
      final String[] paramTypes = ArrayUtil.toStringArray(parametersTypes);
      assert constructorName != null;
      GrMethod grConstructor = factory.createConstructorFromText(constructorName, paramTypes, paramNames, body);
      PsiReferenceList throwsList = grConstructor.getThrowsList();
      for (PsiJavaCodeReferenceElement element : constructor.getThrowsList().getReferenceElements()) {
        throwsList.add(element);
      }
      codeStyleManager.shortenClassReferences(grConstructor);

      grConstructors.add(new GroovyGenerationInfo<>(grConstructor));
    }

    return grConstructors;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
