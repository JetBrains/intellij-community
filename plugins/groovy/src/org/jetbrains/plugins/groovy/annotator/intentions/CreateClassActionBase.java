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

import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public abstract class CreateClassActionBase extends Intention {
  private final GrCreateClassKind myType;

  protected final GrReferenceElement myRefElement;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase");

  public CreateClassActionBase(GrCreateClassKind type, GrReferenceElement refElement) {
    myType = type;
    myRefElement = refElement;
  }

  @Override
  @NotNull
  public String getText() {
    String referenceName = myRefElement.getReferenceName();
    switch (getType()) {
      case TRAIT:
        return GroovyBundle.message("create.trait", referenceName);
      case ENUM:
        return GroovyBundle.message("create.enum", referenceName);
      case CLASS:
        return GroovyBundle.message("create.class.text", referenceName);
      case INTERFACE:
        return GroovyBundle.message("create.interface.text", referenceName);
      case ANNOTATION:
        return GroovyBundle.message("create.annotation.text", referenceName);
      default:
        return "";
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.class.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRefElement.isValid() && ModuleUtilCore.findModuleForPsiElement(myRefElement) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  protected GrCreateClassKind getType() {
    return myType;
  }

  @Nullable
  public static GrTypeDefinition createClassByType(@NotNull final PsiDirectory directory,
                                                   @NotNull final String name,
                                                   @NotNull final PsiManager manager,
                                                   @Nullable final PsiElement contextElement,
                                                   @NotNull final String templateName,
                                                   boolean allowReformatting) {
    AccessToken accessToken = WriteAction.start();

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
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(
              GroovyBundle.message("cannot.create.class.error.text", name, e.getLocalizedMessage()),
              GroovyBundle.message("cannot.create.class.error.title"));
          }
        });
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
    finally {
      accessToken.finish();
    }
  }

  @Nullable
  protected PsiDirectory getTargetDirectory(@NotNull Project project,
                                            @NotNull String qualifier,
                                            @NotNull String name,
                                            @Nullable Module module,
                                            @NotNull String title) {
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

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        return myRefElement.isValid();
      }
    };
  }
}
