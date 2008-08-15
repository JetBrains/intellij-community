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

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

public class NewScriptAction extends NewGroovyActionBase {
  public NewScriptAction() {
    super(GroovyBundle.message("newscript.menu.action.text"),
        GroovyBundle.message("newscript.menu.action.description"),
        GroovyIcons.FILE_TYPE);
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return null;
  }

  protected String getDialogPrompt() {
    return GroovyBundle.message("newscript.dlg.prompt");
  }

  protected String getDialogTitle() {
    return GroovyBundle.message("newscript.dlg.title");
  }

  protected String getCommandName() {
    return GroovyBundle.message("newscript.command.name");
  }

  @NotNull
  protected PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception {
    PsiFile file = createClassFromTemplate(directory, newName, "GroovyScript.groovy");
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());
    PsiElement lastChild = file.getLastChild();
    if (lastChild != null && lastChild.getNode() != null && lastChild.getNode().getElementType() != GroovyTokenTypes.mNLS) {
      file.add(factory.createLineTerminator(1));
    }
    file.add(factory.createLineTerminator(1));
    PsiElement child = file.getLastChild();
    return child != null ? new PsiElement[]{file, child} : new PsiElement[]{file};
  }
}