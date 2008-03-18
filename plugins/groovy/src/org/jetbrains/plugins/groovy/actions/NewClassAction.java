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

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class NewClassAction extends NewActionBase {
  public NewClassAction() {
    super(GroovyBundle.message("newclass.menu.action.text"),
        GroovyBundle.message("newclass.menu.action.description"),
        GroovyIcons.CLASS);
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  protected String getDialogPrompt() {
    return GroovyBundle.message("newclass.dlg.prompt");
  }

  protected String getDialogTitle() {
    return GroovyBundle.message("newclass.dlg.title");
  }

  protected String getCommandName() {
    return GroovyBundle.message("newclass.command.name");
  }

  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception {
    PsiFile file = createClassFromTemplate(directory, newName, "GroovyClass.groovy");
    if (file instanceof GroovyFile) {
      GroovyFile groovyFile = (GroovyFile) file;
      PsiClass[] classes = groovyFile.getClasses();
      if (classes.length == 1 && classes[0] instanceof GrTypeDefinition) {
        GrTypeDefinition definition = (GrTypeDefinition) classes[0];
        PsiElement lbrace = definition.getLBraceGroovy();
        return lbrace != null ? new PsiElement[]{definition, lbrace} : new PsiElement[]{definition};
      }
    }
    return new PsiElement[]{file};
  }
}
