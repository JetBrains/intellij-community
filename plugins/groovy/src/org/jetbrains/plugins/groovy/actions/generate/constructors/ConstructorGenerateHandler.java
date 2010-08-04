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
package org.jetbrains.plugins.groovy.actions.generate.constructors;

import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2008
 */
public class ConstructorGenerateHandler extends GenerateConstructorHandler {

  private static final String DEF_PSEUDO_ANNO = "_____intellij_idea_rulez_def_";

  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    final ClassMember[] classMembers = chooseOriginalMembersImpl(aClass, project);
    if (classMembers == null) return null;

    List<ClassMember> res = new ArrayList<ClassMember>();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    for (ClassMember classMember : classMembers) {
      if (classMember instanceof PsiMethodMember) {
        final PsiMethod method = ((PsiMethodMember)classMember).getElement();
        final PsiMethod copy = (PsiMethod)method.copy();
        if (copy instanceof GrMethod) {
          for (GrParameter parameter : ((GrMethod)copy).getParameterList().getParameters()) {
            if (parameter.getTypeElementGroovy() == null) {
              parameter.setName(DEF_PSEUDO_ANNO + parameter.getName());
            }
          }
        }

        res.add(new PsiMethodMember(factory.createMethodFromText(GroovyToJavaGenerator.generateMethodStub(copy), aClass)));
      } else if (classMember instanceof PsiFieldMember) {
        final PsiField field = ((PsiFieldMember) classMember).getElement();

        String prefix = field instanceof GrField && ((GrField)field).getTypeElementGroovy() == null ? DEF_PSEUDO_ANNO : "";
        res.add(new PsiFieldMember(factory.createFieldFromText(field.getType().getCanonicalText() + " " + prefix + field.getName(), aClass)));
      }
    }

    return res.toArray(new ClassMember[res.size()]);
  }

  @Nullable
  protected ClassMember[] chooseOriginalMembersImpl(PsiClass aClass, Project project) {
    return super.chooseOriginalMembers(aClass, project);
  }

  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    final List<? extends GenerationInfo> list = super.generateMemberPrototypes(aClass, members);

    List<PsiGenerationInfo<GrMethod>> grConstructors = new ArrayList<PsiGenerationInfo<GrMethod>>();

    for (GenerationInfo generationInfo : list) {
      final PsiMember constructorMember = generationInfo.getPsiMember();
      assert constructorMember instanceof PsiMethod;
      final PsiMethod constructor = (PsiMethod) constructorMember;

      final PsiCodeBlock block = constructor.getBody();
      assert block != null;

      final String constructorName = aClass.getName();
      final String body = StringUtil.replace(StringUtil.replace(block.getText(), DEF_PSEUDO_ANNO, ""), ";", "");
      final PsiParameterList list1 = constructor.getParameterList();

      List<String> parametersNames = new ArrayList<String>();
      List<String> parametersTypes = new ArrayList<String>();
      for (PsiParameter parameter : list1.getParameters()) {
        final String fullName = parameter.getName();
        parametersNames.add(StringUtil.trimStart(fullName, DEF_PSEUDO_ANNO));
        parametersTypes.add(fullName.startsWith(DEF_PSEUDO_ANNO) ? null : parameter.getType().getCanonicalText());
      }

      final String[] paramNames = ArrayUtil.toStringArray(parametersNames);
      final String[] paramTypes = ArrayUtil.toStringArray(parametersTypes);
      assert constructorName != null;
      GrMethod grConstructor =
        GroovyPsiElementFactory.getInstance(aClass.getProject()).createConstructorFromText(constructorName, paramTypes, paramNames, body);

      PsiUtil.shortenReferences(grConstructor);

      grConstructors.add(new GroovyGenerationInfo<GrMethod>(grConstructor));
    }

    return grConstructors;
  }

  public boolean startInWriteAction() {
    return true;
  }


}
