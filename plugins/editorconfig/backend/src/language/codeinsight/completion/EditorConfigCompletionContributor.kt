// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import org.editorconfig.language.codeinsight.completion.providers.*

class EditorConfigCompletionContributor : CompletionContributor() {
  init {
    extend(EditorConfigRootDeclarationCompletionProvider)
    extend(EditorConfigRootDeclarationValueCompletionProvider)
    extend(EditorConfigSectionCompletionProvider)

    extend(EditorConfigSimpleOptionKeyCompletionProvider)
    extend(EditorConfigComplexKeyTemplateCompletionProvider)
    extend(EditorConfigComplexKeyFullTemplateCompletionProvider)

    extend(EditorConfigComplexValueCompletionProvider)
  }

  private fun extend(provider: EditorConfigCompletionProviderBase) =
    extend(CompletionType.BASIC, provider.destination, provider)
}
