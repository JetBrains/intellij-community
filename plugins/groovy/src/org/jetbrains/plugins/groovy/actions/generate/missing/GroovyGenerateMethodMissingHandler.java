// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.actions.generate.missing;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateMembersHandlerBase;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
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
           : Collections.emptyList();
  }

  @Nullable
  private static GrMethod genMethod(PsiClass aClass, FileTemplate template) {
    Properties properties = FileTemplateManager.getInstance(aClass.getProject()).getDefaultProperties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, "java.lang.Object");
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, "null");
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
    String fqn = aClass.getQualifiedName();
    if (fqn != null) properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, fqn);
    String className = aClass.getName();
    if (className != null) properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, className);
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
  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
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
      String text = GroovyBundle.message("generate.method.missing.already.defined.warning");

      if (Messages.showYesNoDialog(project, text,
                                   GroovyBundle.message("generate.method.missing.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        final PsiMethod finalMethod = method;
        if (!WriteAction.compute(() -> {
          try {
            finalMethod.delete();
            return Boolean.TRUE;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return Boolean.FALSE;
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

  @Override
  protected ClassMember @Nullable [] chooseMembers(ClassMember[] members,
                                                   boolean allowEmptySelection,
                                                   boolean copyJavadocCheckbox,
                                                   Project project,
                                                   @Nullable Editor editor) {
    return ClassMember.EMPTY_ARRAY;
  }
}
