// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions.generate.constructors;

import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtilRt;
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

public class GroovyGenerateConstructorHandler extends GenerateConstructorHandler {

  private static final String DEF_PSEUDO_ANNO = "_____intellij_idea_rulez_def_";

  @Override
  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
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

    return res.toArray(ClassMember.EMPTY_ARRAY);
  }

  protected ClassMember @Nullable [] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
    return super.chooseOriginalMembers(aClass, project);
  }

  @Override
  protected @NotNull List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members)
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

      final String[] paramNames = ArrayUtilRt.toStringArray(parametersNames);
      final String[] paramTypes = ArrayUtilRt.toStringArray(parametersTypes);
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
}
