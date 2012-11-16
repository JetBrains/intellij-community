/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

/**
 * @author ilyas
 */
public abstract class CreateClassFix {

  public static IntentionAction createClassFromNewAction(final GrNewExpression expression) {
    return new CreateClassActionBase(CreateClassKind.CLASS, expression.getReferenceElement()) {

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase)) return;
        GroovyFileBase groovyFile = (GroovyFileBase)file;
        final PsiManager manager = myRefElement.getManager();

        final String qualifier;
        final String name;
        final Module module;
        final AccessToken accessToken = ReadAction.start();
        try {
          qualifier = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
          name = myRefElement.getReferenceName();
          assert name != null;
          module = findModuleForPsiElement(file);
        }
        finally {
          accessToken.finish();
        }

        PsiDirectory targetDirectory = getTargetDirectory(project, qualifier, name, module, getText());
        if (targetDirectory == null) return;

        GrTypeDefinition targetClass = createClassByType(targetDirectory, name, manager, myRefElement, GroovyTemplates.GROOVY_CLASS);
        if (targetClass == null) return;

        PsiType[] argTypes = getArgTypes(myRefElement);
        if (argTypes != null && argTypes.length > 0) {
          generateConstructor(myRefElement, name, argTypes, targetClass, project);
        }
        else {
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
        addImportForClass(groovyFile, qualifier, targetClass);
      }
    };
  }

  @Nullable
  private static PsiType[] getArgTypes(GrReferenceElement refElement) {
    final AccessToken accessToken = ReadAction.start();
    try {
      return PsiUtil.getArgumentTypes(refElement, false);
    }
    finally {
      accessToken.finish();
    }
  }

  private static void generateConstructor(@NotNull PsiElement refElement,
                                          @NotNull String name,
                                          @NotNull PsiType[] argTypes,
                                          @NotNull GrTypeDefinition targetClass,
                                          @NotNull Project project) {
    final AccessToken writeLock = WriteAction.start();
    try {
      ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
      String[] paramTypes = new String[argTypes.length];
      String[] paramNames = new String[argTypes.length];

      for (int i = 0; i < argTypes.length; i++) {
        PsiType argType = argTypes[i];
        if (argType == null) argType = TypesUtil.getJavaLangObject(refElement);
        paramTypes[i] = "Object";
        paramNames[i] = "o" + i;
        TypeConstraint[] constraints = {SupertypeConstraint.create(argType)};
        paramTypesExpressions[i] = new ChooseTypeExpression(constraints, refElement.getManager(), targetClass.getResolveScope());
      }

      GrMethod method = GroovyPsiElementFactory.getInstance(project).createConstructorFromText(name, paramTypes, paramNames, "{\n}");

      method = (GrMethod)targetClass.addBefore(method, null);
      final PsiElement context = PsiTreeUtil.getParentOfType(refElement, PsiMethod.class, PsiClass.class, PsiFile.class);
      IntentionUtils.createTemplateForMethod(argTypes, paramTypesExpressions, method, targetClass, new TypeConstraint[0], true, context);
    }
    finally {
      writeLock.finish();
    }
  }

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement, CreateClassKind type) {
    return new CreateClassActionBase(type, refElement) {
      public void invoke(@NotNull Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase)) return;
        GroovyFileBase groovyFile = (GroovyFileBase)file;

        PsiElement qualifier = myRefElement.getQualifier();

        if (qualifier == null) {
          createTopLevelClass(project, file, groovyFile);
        }
        else {
          createInnerClass(project, editor, qualifier);
        }
      }

      private void createInnerClass(Project project, final Editor editor, PsiElement qualifier) {
        PsiElement resolved = null;
        if (qualifier instanceof GrCodeReferenceElement) {
          resolved = ((GrCodeReferenceElement)qualifier).resolve();
        }
        else if (qualifier instanceof GrExpression) {
          PsiType type = ((GrExpression)qualifier).getType();
          if (type instanceof PsiClassType) {
            resolved = ((PsiClassType)type).resolve();
          }
        }


        JVMElementFactory factory = JVMElementFactories.getFactory(resolved.getLanguage(), project);
        if (factory == null) return;

        String name = myRefElement.getReferenceName();
        PsiClass template = createTemplate(factory, name);

        if (template == null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (editor != null && editor.getComponent().isDisplayable()) {
                HintManager.getInstance().showErrorHint(editor, GroovyIntentionsBundle.message("cannot.create.class"));
              }
            }
          });
          return;
        }


        AccessToken lock = ApplicationManager.getApplication().acquireWriteActionLock(CreateClassFix.class);
        try {
          if (!(resolved instanceof PsiClass)) return;
          CodeInsightUtilBase.preparePsiElementForWrite(resolved);

          PsiClass added = (PsiClass)resolved.add(template);
          PsiModifierList modifierList = added.getModifierList();
          if (modifierList != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
          }
          putCursor(project, added.getContainingFile(), added);
        }
        finally {
          lock.finish();
        }
      }

      @Nullable
      private PsiClass createTemplate(JVMElementFactory factory, String name) {
        switch (getType()) {
          case ENUM:
            return factory.createEnum(name);
          case CLASS:
            return factory.createClass(name);
          case INTERFACE:
            return factory.createInterface(name);
          case ANNOTATION:
            return factory.createAnnotationType(name);
          default:
            return null;
        }
      }

      private void createTopLevelClass(Project project, PsiFile file, GroovyFileBase groovyFile) {
        final String pack = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
        final PsiManager manager = PsiManager.getInstance(project);
        final String name = myRefElement.getReferenceName();
        assert name != null;
        final Module module = findModuleForPsiElement(file);
        PsiDirectory targetDirectory = getTargetDirectory(project, pack, name, module, getText());
        if (targetDirectory == null) return;

        String templateName = getTemplateName(getType());
        PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement, templateName);
        if (targetClass != null) {
          addImportForClass(groovyFile, pack, targetClass);
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
      }
    };
  }

  private static String getTemplateName(CreateClassKind createClassKind) {
    switch (createClassKind) {
      case ENUM:
        return GroovyTemplates.GROOVY_ENUM;
      case CLASS:
        return GroovyTemplates.GROOVY_CLASS;
      case INTERFACE:
        return GroovyTemplates.GROOVY_INTERFACE;
      case ANNOTATION:
        return GroovyTemplates.GROOVY_ANNOTATION;
      default:
        return null;
    }
  }

  protected static void addImportForClass(@NotNull GroovyFileBase groovyFile, @NotNull String qualifier, @NotNull PsiClass targetClass)
    throws IncorrectOperationException {
    // add import for created class
    String qualifiedName = targetClass.getQualifiedName();
    if (qualifiedName != null && qualifiedName.contains(".")) {
      String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
      if (!packageName.equals(qualifier)) {
        final AccessToken accessToken = WriteAction.start();
        try {
          groovyFile.addImportForClass(targetClass);
        }
        finally {
          accessToken.finish();
        }
      }
    }
  }
}
