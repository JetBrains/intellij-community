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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.CreateTemplateInPackageAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class NewGroovyClassAction extends CreateTemplateInPackageAction implements DumbAware {
  public NewGroovyClassAction() {
    super(GroovyBundle.message("newclass.menu.action.text"), GroovyBundle.message("newclass.menu.action.description"), GroovyIcons.CLASS);
  }

  @Override
  protected CreateFileFromTemplateDialog.Builder buildDialog(Project project, final PsiDirectory directory) {
    final CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog.
      createDialog(project, GroovyBundle.message("newclass.dlg.title"));
    builder.addKind("Class", GroovyIcons.CLASS, "GroovyClass.groovy");
    builder.addKind("Interface", GroovyIcons.INTERFACE, "GroovyInterface.groovy");
    builder.addKind("Enum", GroovyIcons.ENUM, "GroovyEnum.groovy");
    builder.addKind("Annotation", GroovyIcons.ANNOTATION_TYPE, "GroovyAnnotation.groovy");
    return builder;
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return GroovyBundle.message("newclass.menu.action.text");
  }

  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  protected final PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
    return ((GroovyFile)GroovyTemplatesFactory.createFromTemplate(dir, className, className + NewGroovyActionBase.GROOVY_EXTENSION, templateName))
      .getClasses()[0];
  }

}
