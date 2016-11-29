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
package org.jetbrains.plugins.groovy.actions.generate.missing;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateMembersHandlerBase;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.generate.GroovyCodeInsightBundle;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author Max Medvedev
 */
public class GroovyGenerateMethodMissingHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GroovyGenerateMethodMissingHandler.class);

  public GroovyGenerateMethodMissingHandler() {
    super("");
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    return ClassMember.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members)
    throws IncorrectOperationException {

    final String templName = JavaTemplateUtil.TEMPLATE_FROM_USAGE_METHOD_BODY;
    final FileTemplate template = FileTemplateManager.getInstance(aClass.getProject()).getCodeTemplate(templName);

    final GrMethod method = genMethod(aClass, template);
    return method != null
           ? Collections.singletonList(new GroovyGenerationInfo<>(method, true))
           : Collections.<GenerationInfo>emptyList();
  }

  @Nullable
  private static GrMethod genMethod(PsiClass aClass, FileTemplate template) {
    Properties properties = FileTemplateManager.getInstance(aClass.getProject()).getDefaultProperties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, "java.lang.Object");
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, "null");
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, aClass.getQualifiedName());
    properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, aClass.getName());
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, "methodMissing");

    String bodyText;
    try {
      bodyText = StringUtil.replace(template.getText(properties), ";", "");
    }
    catch (IOException e) {
      return null;
    }
    return GroovyPsiElementFactory.getInstance(aClass.getProject())
      .createMethodFromText("def methodMissing(String name, def args) {\n" + bodyText + "\n}");
  }


  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nullable
  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    final PsiMethod[] missings = aClass.findMethodsByName("methodMissing", true);

    PsiMethod method = null;

    for (PsiMethod missing : missings) {
      final PsiParameter[] parameters = missing.getParameterList().getParameters();
      if (parameters.length == 2) {
        if (isNameParam(parameters[0])) {
          method = missing;
        }
      }
    }
    if (method != null) {
      String text = GroovyCodeInsightBundle.message("generate.method.missing.already.defined.warning");

      if (Messages.showYesNoDialog(project, text,
                                   GroovyCodeInsightBundle.message("generate.method.missing.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        final PsiMethod finalMethod = method;
        if (!ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            try {
              finalMethod.delete();
              return Boolean.TRUE;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return Boolean.FALSE;
            }
          }
        }).booleanValue()) {
          return null;
        }
      }
      else {
        return null;
      }
    }

    return new ClassMember[1];
  }

  private static boolean isNameParam(PsiParameter parameter) {
    return parameter.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
           parameter.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Nullable
  @Override
  protected ClassMember[] chooseMembers(ClassMember[] members,
                                        boolean allowEmptySelection,
                                        boolean copyJavadocCheckbox,
                                        Project project,
                                        @Nullable Editor editor) {
    return ClassMember.EMPTY_ARRAY;
  }
}
