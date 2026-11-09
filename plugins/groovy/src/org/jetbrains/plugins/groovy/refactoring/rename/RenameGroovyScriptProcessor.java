// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public final class RenameGroovyScriptProcessor extends RenamePsiFileProcessor
  implements DelegatingHeadlessRenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyFile && ((GroovyFile)element).isScript() && GroovyScriptUtil.isPlainGroovyScript((GroovyFile)element);
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    if (element instanceof GroovyFile && isGroovyFileName(newName)) {
      final PsiClass script = ((GroovyFile)element).getScriptClass();
      if (script != null && script.isValid()) {
        final String scriptName = FileUtilRt.getNameWithoutExtension(newName);
        if (StringUtil.isJavaIdentifier(scriptName)) {
          allRenames.put(script, scriptName);
        }
      }
    }
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName, @NotNull MultiMap<PsiElement, String> conflicts) {
    if (!isGroovyFileName(newName)) return;
    final String scriptName = FileUtilRt.getNameWithoutExtension(newName);
    if (!StringUtil.isJavaIdentifier(scriptName)) {
      final PsiClass script = ((GroovyFile)element).getScriptClass();
      conflicts.putValue(script, GroovyRefactoringBundle.message("cannot.rename.script.class.to.0", script.getName(), scriptName));
    }
  }

  /** Returns true when a file with {@code fileName} is a Groovy file. */
  private static boolean isGroovyFileName(@NotNull String fileName) {
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    return fileType instanceof LanguageFileType languageFileType && languageFileType.getLanguage() == GroovyLanguage.INSTANCE;
  }
}
