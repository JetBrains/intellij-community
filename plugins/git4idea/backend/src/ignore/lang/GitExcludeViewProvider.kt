// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore.lang

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider

class GitExcludeViewProvider(manager: PsiManager,
                             virtualFile: VirtualFile,
                             eventSystemEnabled: Boolean,
                             language: Language) : SingleRootFileViewProvider(manager, virtualFile, eventSystemEnabled, language) {

  override fun shouldCreatePsi() = baseLanguage is GitExcludeLanguage
}

class GitExcludeViewProviderFactory : FileViewProviderFactory {

  override fun createFileViewProvider(file: VirtualFile,
                                      language: Language,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean) =
    GitExcludeViewProvider(manager, file, eventSystemEnabled, language)
}