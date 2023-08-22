// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.io.IOException;
import java.util.Properties;

import static com.intellij.psi.util.PointersKt.createSmartPointer;

public class AddMethodFix extends GroovyFix {
  private static final Logger LOG = Logger.getInstance(AddMethodFix.class);
  private final String myMethodName;
  private final String myClassName;
  private final SmartPsiElementPointer<GrTypeDefinition> myPsiClassPointer;

  public AddMethodFix(@NotNull String methodName, @NotNull GrTypeDefinition aClass) {
    myMethodName = methodName;
    myClassName = aClass.getName();
    myPsiClassPointer = createSmartPointer(aClass);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    GrTypeDefinition definition = myPsiClassPointer.getElement();
    if (definition == null) {
      return null;
    }
    GrTypeDefinition copy = PsiTreeUtil.findSameElementInCopy(definition, target);
    return new AddMethodFix(myMethodName, copy);
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    GrTypeDefinition psiClass = myPsiClassPointer.getElement();
    if (psiClass == null) return;

    if (psiClass.isInterface()) {
      final GrMethod method = GroovyPsiElementFactory.getInstance(project).createMethodFromText(
        "def " + psiClass.getName() + " " + myMethodName + "();"
      );
      psiClass.add(method);
    }
    else {
      String templName = JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY;
      final FileTemplate template = FileTemplateManager.getInstance(project).getCodeTemplate(templName);

      Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();

      String returnType = generateTypeText(psiClass);
      properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType);
      properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                             PsiTypesUtil.getDefaultValueOfType(JavaPsiFacade.getElementFactory(project).createType(psiClass)));
      properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, "");
      properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, psiClass.getQualifiedName());
      properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, psiClass.getName());
      properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, myMethodName);

      try {
        String bodyText = StringUtil.replace(template.getText(properties), ";", "");
        final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n" + bodyText + "\n");

        final GrMethod method = GroovyPsiElementFactory.getInstance(project).createMethodFromText(
          "", myMethodName, returnType, ArrayUtilRt.EMPTY_STRING_ARRAY, psiClass
        );
        method.setBlock(newBody);
        psiClass.add(method);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }


  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("add.method", myMethodName, myClassName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("add.method.family");
  }

  static String generateTypeText(GrTypeDefinition aClass) {
    String className = aClass.getName();
    LOG.assertTrue(className != null, aClass);
    StringBuilder returnType = new StringBuilder(className);
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    if (typeParameters.length > 0) {
      returnType.append('<');
      for (PsiTypeParameter typeParameter : typeParameters) {
        returnType.append(typeParameter.getName()).append(", ");
      }
      returnType.replace(returnType.length() - 2, returnType.length(), ">");
    }
    return returnType.toString();
  }
}
