/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

import java.io.IOException;
import java.util.Properties;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil");

  private GroovyOverrideImplementUtil() {
  }

  public static GrMethod generateMethodPrototype(GrTypeDefinition aClass,
                                                 PsiMethod method,
                                                 PsiSubstitutor substitutor) {
    final Project project = aClass.getProject();
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    String templName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
    final GrMethod result = (GrMethod)GenerateMembersUtil.substituteGenericMethod(method, substitutor, aClass);

    setupModifierList(result);
    setupOverridingMethodBody(project, method, result, template, substitutor);
    setupReturnType(result, method);

    setupAnnotations(aClass, method, result);

    GroovyChangeContextUtil.encodeContextInfo(result);
    return result;
  }

  private static void setupReturnType(GrMethod result, PsiMethod method) {
    if (method instanceof GrMethod && ((GrMethod)method).getReturnTypeElementGroovy() == null) {
      result.setReturnType(null);
      GrModifierList modifierList = result.getModifierList();
      if (!modifierList.hasExplicitVisibilityModifiers()) {
        modifierList.setModifierProperty(GrModifier.DEF, true);
      }
    }
  }

  private static void setupAnnotations(@NotNull GrTypeDefinition aClass, @NotNull PsiMethod method, @NotNull GrMethod result) {
    if (OverrideImplementUtil.isInsertOverride(method, aClass)) {
      result.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    final PsiParameter[] originalParams = method.getParameterList().getParameters();

    GrParameter[] parameters = result.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      final PsiParameter original = originalParams[i];

      for (PsiAnnotation annotation : original.getModifierList().getAnnotations()) {
        final GrModifierList modifierList = parameter.getModifierList();

        String qname = annotation.getQualifiedName();

        if (annotation instanceof GrAnnotation) {
          modifierList.add(annotation);
        }
        else {
          modifierList.add(factory.createAnnotationFromText(annotation.getText()));
        }
      }
    }
  }

  private static void setupModifierList(GrMethod result) {
    PsiModifierList modifierList = result.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    modifierList.setModifierProperty(PsiModifier.NATIVE, false);
  }


  @Nullable
  private static PsiType getSuperReturnType(@NotNull PsiMethod superMethod) {
    if (superMethod instanceof GrMethod) {
      final GrTypeElement element = ((GrMethod)superMethod).getReturnTypeElementGroovy();
      return element != null ? element.getType() : null;
    }

    return superMethod.getReturnType();
  }

  private static void setupOverridingMethodBody(Project project,
                                                PsiMethod method,
                                                GrMethod resultMethod,
                                                FileTemplate template,
                                                PsiSubstitutor substitutor) {
    final PsiType returnType = substitutor.substitute(getSuperReturnType(method));

    String returnTypeText = "";
    if (returnType != null) {
      returnTypeText = returnType.getPresentableText();
    }
    Properties properties = FileTemplateManager.getInstance().getDefaultProperties(project);

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnTypeText);
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(method, resultMethod));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, method.getContainingClass(), resultMethod);

    try {
      String bodyText = StringUtil.replace(template.getText(properties), ";", "");
      final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n " + bodyText + "\n");

      resultMethod.setBlock(newBody);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
      buffer.append("return ");
    }
    buffer.append("super");
    PsiParameter[] parms = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()) {
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parms.length; i++) {
      String name = parms[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }
}
