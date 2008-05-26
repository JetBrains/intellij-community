/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.grails.lang.gsp.psi.gsp.api.GspFile;

/**
 * @author ilyas
 */
public abstract class CreateClassFix {

  public static IntentionAction createClassFromNewAction(final GrNewExpression expression) {
    return new CreateClassActionBase(expression.getReferenceElement()) {
      GrNewExpression myNewExpression = expression;

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase || file instanceof GspFile)) return;
        final PsiManager manager = myRefElement.getManager();
        final String name = myRefElement.getReferenceName();
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        GroovyFileBase groovyFile = file instanceof GspFile ? ((GspFile) file).getGroovyLanguageRoot() :
            (GroovyFileBase) file;
        final String qualifier = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
        String title = GroovyBundle.message("create.class.family.name");
        GroovyCreateClassDialog dialog = new GroovyCreateClassDialog(project, title, name, qualifier, module);
        dialog.show();

        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        if (targetDirectory == null) return;

        PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement);
        if (targetClass != null) {
          // add import for created class
          String qualifiedName = targetClass.getQualifiedName();
          if (qualifiedName != null && qualifiedName.contains(".")) {
            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
            if (!packageName.equals(qualifier)) {
              groovyFile.addImportForClass(targetClass);
            }
          }
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
      }

    };
  }

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement) {
    return new CreateClassActionBase(refElement) {

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase || file instanceof GspFile)) return;
        final PsiManager manager = myRefElement.getManager();
        final String name = myRefElement.getReferenceName();
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        GroovyFileBase groovyFile = file instanceof GspFile ? ((GspFile) file).getGroovyLanguageRoot() :
            (GroovyFileBase) file;
        final String qualifier = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
        String title = GroovyBundle.message("create.class.family.name");
        GroovyCreateClassDialog dialog = new GroovyCreateClassDialog(project, title, name, qualifier, module);
        dialog.show();

        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;

        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        if (targetDirectory == null) return;

        PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement);
        if (targetClass != null) {
          // add import for created class
          String qualifiedName = targetClass.getQualifiedName();
          if (qualifiedName != null && qualifiedName.contains(".")) {
            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
            if (!packageName.equals(qualifier)) {
              groovyFile.addImportForClass(targetClass);
            }
          }
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
      }

    };
  }

}
