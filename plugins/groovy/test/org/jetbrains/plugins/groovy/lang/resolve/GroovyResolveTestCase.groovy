// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

abstract class GroovyResolveTestCase extends LightGroovyTestCase {
  @NonNls protected static final String MARKER = "<ref>"

  @Override
  void setUp() {
    super.setUp()
    if (new File("$myFixture.testDataPath/${getTestName(true)}").exists()) {
      myFixture.copyDirectoryToProject(getTestName(true), "")
    }
  }

  protected <T extends PsiReference> T configureByFile(@NonNls String filePath, @Nullable String newFilePath = null, Class<T> refType = PsiReference) {
    def trimmedFilePath = StringUtil.trimStart(filePath, getTestName(true) + "/")
    VirtualFile vFile = myFixture.tempDirFixture.getFile(filePath)
    if (vFile == null) {
      vFile = myFixture.tempDirFixture.getFile(trimmedFilePath)
    }
    assertNotNull("file $filePath not found", vFile)

    String fileText
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile))
    }
    catch (IOException e) {
      throw new RuntimeException(e)
    }

    int offset = fileText.indexOf(MARKER)
    assertTrue("'ref' marker is not found", offset >= 0)
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length())

    if (newFilePath == null) {
      myFixture.configureByText(vFile.getName(), fileText)
    }
    else {
      myFixture.configureByText(newFilePath, fileText)
    }

    PsiReference ref = myFixture.file.findReferenceAt(offset)
    assertInstanceOf(ref, refType)
    return ref
  }

  protected <T extends PsiReference> T configureByText(String fileName = '_a.groovy', String text, Class<T> refType = PsiReference) {
    myFixture.configureByText fileName, text
    final ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertInstanceOf(ref, refType)
    return ref
  }

  @Nullable
  protected <T extends PsiElement> T resolve(String fileName = getTestName(false) + ".groovy", Class<T> type = null) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName)
    assertNotNull(ref)
    final resolved = ref.resolve()
    if (type != null) assertInstanceOf(resolved, type)
    return resolved
  }

  @Nullable
  protected <T extends PsiElement> T resolveByText(String text, Class<T> type = PsiElement) {
    final ref = configureByText(text)
    assertNotNull(ref)
    final resolved = ref.resolve()
    if (type == null) {
      assertNull(resolved)
    }
    else {
      assertInstanceOf(resolved, type)
    }
    return (T)resolved
  }

  @Nullable
  protected GroovyResolveResult advancedResolve(String fileName) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName)
    assertInstanceOf(ref, GrReferenceExpression.class)
    return ((GrReferenceExpression)ref).advancedResolve()
  }
}
