// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.TextFieldWithAutoCompletion


class PlainTextSupportTest : GrazieTestBase() {
  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.RUSSIAN))
    runHighlightTestForFileUsingGrazieSpellchecker("ide/language/plain/Example.txt")
  }

  fun `test no grammar checks in TextFieldWithAutoCompletion`() {
    val field = TextFieldWithAutoCompletion(
      project, TextFieldWithAutoCompletion.StringsCompletionProvider(emptyList(), null), true, "I have an new apple here.")
    field.addNotify()
    disposeOnTearDown(Disposable { field.removeNotify() })
    myFixture.configureFromExistingVirtualFile(FileDocumentManager.getInstance().getFile(field.editor!!.document)!!)
    myFixture.checkHighlighting()
  }
}
