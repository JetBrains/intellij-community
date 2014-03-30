/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public class RenameGroovyScriptProcessor extends RenamePsiFileProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyFile && ((GroovyFile)element).isScript();
  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    if (element instanceof GroovyFile) {
      final PsiClass script = ((GroovyFile)element).getScriptClass();
      if (script != null && script.isValid()) {
        final String scriptName = FileUtil.getNameWithoutExtension(newName);
        if (StringUtil.isJavaIdentifier(scriptName)) {
          allRenames.put(script, scriptName);
        }
      }
    }
  }

  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    final String scriptName = FileUtil.getNameWithoutExtension(newName);
    if (!StringUtil.isJavaIdentifier(scriptName)) {
      final PsiClass script = ((GroovyFile)element).getScriptClass();
      conflicts.putValue(script, GroovyRefactoringBundle.message("cannot.rename.script.class.to.0", script.getName(), scriptName));
    }
  }
}
