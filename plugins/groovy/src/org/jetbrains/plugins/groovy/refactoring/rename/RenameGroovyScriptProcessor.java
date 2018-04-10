// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
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
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName, @NotNull MultiMap<PsiElement, String> conflicts) {
    final String scriptName = FileUtil.getNameWithoutExtension(newName);
    if (!StringUtil.isJavaIdentifier(scriptName)) {
      final PsiClass script = ((GroovyFile)element).getScriptClass();
      conflicts.putValue(script, GroovyRefactoringBundle.message("cannot.rename.script.class.to.0", script.getName(), scriptName));
    }
  }
}
