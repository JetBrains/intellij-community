/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public abstract class CreateClassFix {

  public static IntentionAction createClassFromNewAction(final GrNewExpression expression) {
    return new CreateClassActionBase(expression.getReferenceElement()) {
      GrNewExpression myNewExpression = expression;

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase)) return;
        GroovyFileBase groovyFile = (GroovyFileBase) file;
        final String qualifier = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
        final PsiManager manager = myRefElement.getManager();
        final String name = myRefElement.getReferenceName();
        assert name != null;
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        PsiDirectory targetDirectory = getTargetDirectory(project, qualifier, name, module);
        if (targetDirectory == null) return;

        PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement);

        GrArgumentList argList = expression.getArgumentList();
        if (argList != null &&
            argList.getNamedArguments().length + argList.getExpressionArguments().length > 0 &&
            targetClass instanceof GrMemberOwner) {

          PsiType[] argTypes = PsiUtil.getArgumentTypes(myRefElement, false);
          assert argTypes != null;

          ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
          String[] paramTypes = new String[argTypes.length];
          String[] paramNames = new String[argTypes.length];

          for (int i = 0; i < argTypes.length; i++) {
            PsiType argType = argTypes[i];
            if (argType == null) argType = TypesUtil.getJavaLangObject(myRefElement);
            paramTypes[i] = "Object";
            paramNames[i] = "o" + i;
            paramTypesExpressions[i] = new ChooseTypeExpression(new TypeConstraint[]{SupertypeConstraint.create(argType)}, myRefElement.getManager());
          }

          GrMethod method = GroovyPsiElementFactory.getInstance(project).createConstructorFromText(name, paramTypes, paramNames, "{\n}");
          GrMemberOwner owner = (GrMemberOwner) targetClass;
          method = owner.addMemberDeclaration(method, null);
          IntentionUtils.createTemplateForMethod(argTypes, paramTypesExpressions, method, owner, new TypeConstraint[0], true);
        } else {
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
        addImportForClass(groovyFile, qualifier, targetClass);
      }

    };
  }

  public static IntentionAction createClassFixAction(final GrReferenceElement refElement) {
    return new CreateClassActionBase(refElement) {

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof GroovyFileBase)) return;
        GroovyFileBase groovyFile = (GroovyFileBase) file;
        final String qualifier = groovyFile instanceof GroovyFile ? groovyFile.getPackageName() : "";
        final PsiManager manager = PsiManager.getInstance(project);
        final String name = myRefElement.getReferenceName();
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        PsiDirectory targetDirectory = getTargetDirectory(project, qualifier, name, module);
        if (targetDirectory == null) return;

        PsiClass targetClass = createClassByType(targetDirectory, name, manager, myRefElement);
        if (targetClass != null) {
          addImportForClass(groovyFile, qualifier, targetClass);
          putCursor(project, targetClass.getContainingFile(), targetClass);
        }
      }

    };
  }

  private static PsiDirectory getTargetDirectory(Project project, String qualifier, String name, Module module) {
    String title = GroovyBundle.message("create.class.family.name");
    GroovyCreateClassDialog dialog = new GroovyCreateClassDialog(project, title, name, qualifier, module);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;
    return dialog.getTargetDirectory();
  }

  protected static void addImportForClass(GroovyFileBase groovyFile, String qualifier, PsiClass targetClass) throws IncorrectOperationException {
    if (targetClass != null) {
      // add import for created class
      String qualifiedName = targetClass.getQualifiedName();
      if (qualifiedName != null && qualifiedName.contains(".")) {
        String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
        if (!packageName.equals(qualifier)) {
          groovyFile.addImportForClass(targetClass);
        }
      }
    }
  }

}
