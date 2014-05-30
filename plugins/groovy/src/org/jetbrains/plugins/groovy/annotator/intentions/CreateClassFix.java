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

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

/**
 * @author ilyas
 */
public abstract class CreateClassFix {

  public static IntentionAction createClassFromNewAction(final GrNewExpression expression) {
    return new CreateClassActionBase(GrCreateClassKind.CLASS, expression.getReferenceElement()) {

      @Override
      protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final PsiFile file = element.getContainingFile();
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
          module = ModuleUtilCore.findModuleForPsiElement(file);
        }
        finally {
          accessToken.finish();
        }

        PsiDirectory targetDirectory = getTargetDirectory(project, qualifier, name, module, getText());
        if (targetDirectory == null) return;

        final GrTypeDefinition targetClass = createClassByType(targetDirectory, name, manager, myRefElement, GroovyTemplates.GROOVY_CLASS,
                                                               true);
        if (targetClass == null) return;

        PsiType[] argTypes = getArgTypes(myRefElement);
        if (argTypes != null && argTypes.length > 0) {
          generateConstructor(myRefElement, name, argTypes, targetClass, project);
          bindRef(targetClass, myRefElement);
        }
        else {
          bindRef(targetClass, myRefElement);
          IntentionUtils.positionCursor(project, targetClass.getContainingFile(), targetClass);
        }
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

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement, GrCreateClassKind type) {
    return new CreateClassActionBase(type, refElement) {
      @Override
      protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final PsiFile file = element.getContainingFile();
        if (!(file instanceof GroovyFileBase)) return;
        GroovyFileBase groovyFile = (GroovyFileBase)file;

        PsiElement qualifier = myRefElement.getQualifier();

        if (qualifier == null ||
            qualifier instanceof GrReferenceElement && ((GrReferenceElement)qualifier).resolve() instanceof PsiPackage) {
          createTopLevelClass(project, groovyFile);
        }
        else {
          createInnerClass(project, editor, qualifier);
        }
      }

      private void createInnerClass(Project project, final Editor editor, PsiElement qualifier) {
        PsiElement resolved = resolveQualifier(qualifier);
        if (!(resolved instanceof PsiClass)) return;

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
          FileModificationService.getInstance().preparePsiElementForWrite(resolved);

          PsiClass added = (PsiClass)resolved.add(template);
          PsiModifierList modifierList = added.getModifierList();
          if (modifierList != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
          }
          IntentionUtils.positionCursor(project, added.getContainingFile(), added);
        }
        finally {
          lock.finish();
        }
      }

      @Nullable
      private PsiElement resolveQualifier(@NotNull PsiElement qualifier) {
        if (qualifier instanceof GrCodeReferenceElement) {
          return ((GrCodeReferenceElement)qualifier).resolve();
        }
        else if (qualifier instanceof GrExpression) {
          PsiType type = ((GrExpression)qualifier).getType();
          if (type instanceof PsiClassType) {
            return ((PsiClassType)type).resolve();
          }
          else if (qualifier instanceof GrReferenceExpression) {
            final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
            if (resolved instanceof PsiClass || resolved instanceof PsiPackage) {
              return resolved;
            }
          }
        }

        return null;
      }

      @Nullable
      private PsiClass createTemplate(JVMElementFactory factory, String name) {
        switch (getType()) {
          case ENUM:
            return factory.createEnum(name);
          case TRAIT:
            if (factory instanceof GroovyPsiElementFactory) {
              return ((GroovyPsiElementFactory)factory).createTrait(name);
            }
            else {
              return null;
            }
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

      private void createTopLevelClass(@NotNull Project project, @NotNull GroovyFileBase file) {
        final String pack = getPackage(file);
        final PsiManager manager = PsiManager.getInstance(project);
        final String name = myRefElement.getReferenceName();
        assert name != null;
        final Module module = ModuleUtilCore.findModuleForPsiElement(file);
        PsiDirectory targetDirectory = getTargetDirectory(project, pack, name, module, getText());
        if (targetDirectory == null) return;

        String templateName = getTemplateName(getType());
        final PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement, templateName, true);
        if (targetClass == null) return;

        bindRef(targetClass, myRefElement);
        IntentionUtils.positionCursor(project, targetClass.getContainingFile(), targetClass);
      }

      @NotNull
      private String getPackage(@NotNull PsiClassOwner file) {
        final PsiElement qualifier = myRefElement.getQualifier();
        if (qualifier instanceof GrReferenceElement) {
          final PsiElement resolved = ((GrReferenceElement)qualifier).resolve();
          if (resolved instanceof PsiPackage) {
            return ((PsiPackage)resolved).getQualifiedName();
          }
        }
        return file instanceof GroovyFile ? file.getPackageName() : "";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!super.isAvailable(project, editor, file)) return false;

        final PsiElement qualifier = myRefElement.getQualifier();
        if (qualifier != null && resolveQualifier(qualifier) == null) {
          return false;
        }

        return true;
      }
    };
  }

  private static void bindRef(@NotNull final PsiClass targetClass, @NotNull final GrReferenceElement ref) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final PsiElement newRef = ref.bindToElement(targetClass);
        JavaCodeStyleManager.getInstance(targetClass.getProject()).shortenClassReferences(newRef);
      }
    });
  }

  private static String getTemplateName(GrCreateClassKind createClassKind) {
    switch (createClassKind) {
      case TRAIT:
        return GroovyTemplates.GROOVY_TRAIT;
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
}
