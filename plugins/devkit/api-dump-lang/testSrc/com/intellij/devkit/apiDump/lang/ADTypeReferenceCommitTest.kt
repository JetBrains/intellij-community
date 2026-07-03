// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.devkit.apiDump.lang.psi.ADTypeReference
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class ADTypeReferenceCommitTest : BasePlatformTestCase() {

  // Regression test: editing a type reference used to throw StringIndexOutOfBoundsException
  // from JavaClassReferenceSet.reparse during ADTypeReferenceImpl.subtreeChanged() while the
  // PSI tree was mid-mutation on document commit.
  fun testEditingTypeReferenceDoesNotThrowOnCommit() {
    val file = myFixture.configureByText("api-dump.txt", "c:com.intellij.execution.ExecutionException\n")

    // Force the JavaClassReferenceSet to be created and cached, as happens during highlighting.
    val typeRef = PsiTreeUtil.findChildOfType(file, ADTypeReference::class.java)!!
    assertTrue(typeRef.references.isNotEmpty())

    val document = myFixture.editor.document
    val offset = typeRef.textRange.startOffset + "com.intellij".length
    WriteCommandAction.runWriteCommandAction(project) {
      // Editing inside the reference triggers an incremental reparse whose PSI diff invalidates
      // a leaf under the type reference, invoking subtreeChanged() during the commit.
      document.insertString(offset, "X")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    // The reference set is rebuilt lazily and stays consistent with the new text.
    val typeRefAfter = PsiTreeUtil.findChildOfType(myFixture.file, ADTypeReference::class.java)!!
    assertTrue(typeRefAfter.references.isNotEmpty())
  }
}
