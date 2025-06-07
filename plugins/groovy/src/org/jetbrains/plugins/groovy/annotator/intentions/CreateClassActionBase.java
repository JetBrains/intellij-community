// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public abstract class CreateClassActionBase extends Intention {
  private final GrCreateClassKind myType;

  protected final GrReferenceElement myRefElement;
  private static final Logger LOG = Logger.getInstance(CreateClassActionBase.class);

  public CreateClassActionBase(GrCreateClassKind type, GrReferenceElement refElement) {
    myType = type;
    myRefElement = refElement;
  }

  @Override
  public @NotNull String getText() {
    String referenceName = myRefElement.getReferenceName();
    return switch (getType()) {
      case TRAIT -> GroovyBundle.message("create.trait", referenceName);
      case ENUM -> GroovyBundle.message("create.enum", referenceName);
      case CLASS -> GroovyBundle.message("create.class.text", referenceName);
      case INTERFACE -> GroovyBundle.message("create.interface.text", referenceName);
      case ANNOTATION -> GroovyBundle.message("create.annotation.text", referenceName);
      case RECORD -> GroovyBundle.message("create.record.text", referenceName);
    };
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    String name = myRefElement.getReferenceName();
    if (name == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiFile containingFile = myRefElement.getContainingFile();
    if (!(containingFile instanceof GroovyFileBase)) {
      return IntentionPreviewInfo.EMPTY;
    }
    String packageName = ((GroovyFileBase)containingFile).getPackageName();
    String prefix = packageName.isEmpty() ? "" : "package " + packageName + "\n\n";
    String template = prefix + "%s " + name + " {\n}" ;
    String newClassPrefix = switch (myType) {
      case CLASS -> "class";
      case INTERFACE -> "interface";
      case TRAIT -> "trait";
      case ENUM -> "enum";
      case ANNOTATION -> "@interface";
      case RECORD -> "record";
    };

    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, name + ".groovy", "", String.format(template, newClassPrefix));
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("create.class.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myRefElement.isValid() && ModuleUtilCore.findModuleForPsiElement(myRefElement) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  protected GrCreateClassKind getType() {
    return myType;
  }

  public static @Nullable GrTypeDefinition createClassByType(final @NotNull PsiDirectory directory,
                                                             final @NotNull String name,
                                                             final @NotNull PsiManager manager,
                                                             final @Nullable PsiElement contextElement,
                                                             final @NotNull String templateName,
                                                             boolean allowReformatting) {
    return WriteAction.compute(() -> {
      try {
        GrTypeDefinition targetClass = null;
        try {
          PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, name, name + ".groovy", templateName, allowReformatting);
          for (PsiElement element : file.getChildren()) {
            if (element instanceof GrTypeDefinition) {
              targetClass = ((GrTypeDefinition)element);
              break;
            }
          }
          if (targetClass == null) {
            throw new IncorrectOperationException(GroovyBundle.message("no.class.in.file.template"));
          }
        }
        catch (final IncorrectOperationException e) {
          ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
            GroovyBundle.message("cannot.create.class.error.text", name, e.getLocalizedMessage()),
            GroovyBundle.message("cannot.create.class.error.title")));
          return null;
        }
        PsiModifierList modifiers = targetClass.getModifierList();
        if (contextElement != null &&
            !JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().isAccessible(targetClass, contextElement, null) &&
            modifiers != null) {
          modifiers.setModifierProperty(PsiModifier.PUBLIC, true);
        }
        return targetClass;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    });
  }

  protected @Nullable PsiDirectory getTargetDirectory(@NotNull Project project,
                                                      @NotNull String qualifier,
                                                      @NotNull String name,
                                                      @Nullable Module module,
                                                      @DialogTitle @NotNull String title) {
    CreateClassDialog dialog = new CreateClassDialog(project, title, name, qualifier, getType(), false, module) {
      @Override
      protected boolean reportBaseInSourceSelectionInTest() {
        return true;
      }
    };
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

    return dialog.getTargetDirectory();
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return myRefElement.isValid();
      }
    };
  }
}
