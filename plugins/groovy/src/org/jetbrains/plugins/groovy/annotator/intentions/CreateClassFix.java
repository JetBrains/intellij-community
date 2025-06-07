// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
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

import static org.jetbrains.plugins.groovy.intentions.base.IntentionUtils.createTemplateForMethod;

public abstract class CreateClassFix {

  public static IntentionAction createClassFromNewAction(final GrNewExpression expression) {
    return new CreateClassActionBase(GrCreateClassKind.CLASS, expression.getReferenceElement()) {

      @Override
      protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
        final PsiFile file = element.getContainingFile();
        if (!(file instanceof GroovyFileBase groovyFile)) return;
        final PsiManager manager = myRefElement.getManager();

        final String qualifier = ReadAction.compute(() -> groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "");
        final String name = ReadAction.compute(() -> myRefElement.getReferenceName());
        assert name != null;
        final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(file));

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

  private static PsiType @Nullable [] getArgTypes(GrReferenceElement refElement) {
    return ReadAction.compute(() -> PsiUtil.getArgumentTypes(refElement, false));
  }

  private static void generateConstructor(@NotNull PsiElement refElement,
                                          @NotNull String name,
                                          PsiType @NotNull [] argTypes,
                                          @NotNull GrTypeDefinition targetClass,
                                          @NotNull Project project) {
    WriteAction.run(() -> {
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
      createTemplateForMethod(paramTypesExpressions, method, targetClass, TypeConstraint.EMPTY_ARRAY, true, context);
    });
  }

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement, GrCreateClassKind type) {
    return new CreateClassActionBase(type, refElement) {
      @Override
      protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
        final PsiFile file = element.getContainingFile();
        if (!(file instanceof GroovyFileBase groovyFile)) return;

        PsiElement qualifier = myRefElement.getQualifier();

        if (qualifier == null ||
            qualifier instanceof GrReferenceElement && ((GrReferenceElement<?>)qualifier).resolve() instanceof PsiPackage) {
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
          ApplicationManager.getApplication().invokeLater(() -> {
            if (editor != null && editor.getComponent().isDisplayable()) {
              HintManager.getInstance().showErrorHint(editor, GroovyBundle.message("cannot.create.class"));
            }
          });
          return;
        }

        if (!FileModificationService.getInstance().preparePsiElementForWrite(resolved)) return;

        WriteAction.run(() -> {
          PsiClass added = (PsiClass)resolved.add(template);
          PsiModifierList modifierList = added.getModifierList();
          if (modifierList != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
          }
          IntentionUtils.positionCursor(project, added.getContainingFile(), added);
        });
      }

      private static @Nullable PsiElement resolveQualifier(@NotNull PsiElement qualifier) {
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

      private @Nullable PsiClass createTemplate(JVMElementFactory factory, String name) {
        return switch (getType()) {
          case ENUM -> factory.createEnum(name);
          case TRAIT -> {
            if (factory instanceof GroovyPsiElementFactory groovyFactory) {
              yield groovyFactory.createTrait(name);
            }
            yield null;
          }
          case CLASS -> factory.createClass(name);
          case INTERFACE -> factory.createInterface(name);
          case ANNOTATION -> factory.createAnnotationType(name);
          case RECORD -> {
            if (factory instanceof GroovyPsiElementFactory groovyFactory) {
              yield groovyFactory.createRecord(name);
            }
            yield null;
          }
        };
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

      private @NotNull String getPackage(@NotNull PsiClassOwner file) {
        final PsiElement qualifier = myRefElement.getQualifier();
        if (qualifier instanceof GrReferenceElement) {
          final PsiElement resolved = ((GrReferenceElement<?>)qualifier).resolve();
          if (resolved instanceof PsiPackage) {
            return ((PsiPackage)resolved).getQualifiedName();
          }
        }
        return file instanceof GroovyFile ? file.getPackageName() : "";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (!super.isAvailable(project, editor, psiFile)) return false;

        final PsiElement qualifier = myRefElement.getQualifier();
        if (qualifier != null && resolveQualifier(qualifier) == null) {
          return false;
        }

        return true;
      }
    };
  }

  private static void bindRef(final @NotNull PsiClass targetClass, final @NotNull GrReferenceElement ref) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiElement newRef = ref.bindToElement(targetClass);
      JavaCodeStyleManager.getInstance(targetClass.getProject()).shortenClassReferences(newRef);
    });
  }

  private static String getTemplateName(GrCreateClassKind createClassKind) {
    return switch (createClassKind) {
      case TRAIT -> GroovyTemplates.GROOVY_TRAIT;
      case ENUM -> GroovyTemplates.GROOVY_ENUM;
      case CLASS -> GroovyTemplates.GROOVY_CLASS;
      case INTERFACE -> GroovyTemplates.GROOVY_INTERFACE;
      case ANNOTATION -> GroovyTemplates.GROOVY_ANNOTATION;
      case RECORD -> GroovyTemplates.GROOVY_RECORD;
    };
  }
}
