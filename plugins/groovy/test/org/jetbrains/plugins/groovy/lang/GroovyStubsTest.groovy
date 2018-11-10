// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType

import static org.jetbrains.plugins.groovy.util.TestUtils.disableAstLoading

/**
 * @author peter
 */
class GroovyStubsTest extends LightCodeInsightFixtureTestCase {

  void testEnumConstant() {
    myFixture.tempDirFixture.createFile('A.groovy', 'enum A { MyEnumConstant }')
    GrEnumConstant ec = (GrEnumConstant)PsiShortNamesCache.getInstance(project).getFieldsByName("MyEnumConstant", GlobalSearchScope.allScope(project))[0]
    def file = (PsiFileImpl)ec.containingFile
    assert file.stub
    assert ec.containingClass.qualifiedName == 'A'
    assert file.stub

    assert ec in ec.containingClass.fields
    assert ec in ((GrEnumDefinitionBody)((GrTypeDefinition)ec.containingClass).body).enumConstantList.enumConstants
    assert file.stub
  }

  void testStubIndexMismatch() {
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).disableBackgroundCommit(myFixture.testRootDisposable)

    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.groovy")
    final Project project = myFixture.getProject()
    PsiFileImpl fooFile = (PsiFileImpl) PsiManager.getInstance(project).findFile(vFile)
    final Document fooDocument = fooFile.getViewProvider().getDocument()
    assert !JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))
    WriteCommandAction.writeCommandAction(project, fooFile).run ({
        fooDocument.setText("class Fooxx {}")
    } as ThrowableRunnable)
    PsiDocumentManager.getInstance(project).commitDocument(fooDocument)
    fooFile.setTreeElementPointer(null)
    DumbServiceImpl.getInstance(project).setDumb(true)
    try {
      assertOneElement(((GroovyFile) fooFile).classes)
      assertFalse(fooFile.isContentsLoaded())
    }
    finally {
      DumbServiceImpl.getInstance(project).setDumb(false)
    }
    assert JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))
  }

  void 'test error in code reference'() {
    myFixture.tempDirFixture.createFile('A.groovy', 'class A extends foo.B< {}')
    disableAstLoading project, testRootDisposable
    def clazz = myFixture.findClass("A")
    assert clazz != null
    def extendsTypes = clazz.extendsListTypes
    assert extendsTypes.size() == 1
    def type = extendsTypes.first()
    assert type instanceof GrClassReferenceType
    def reference = type.reference
    assert reference instanceof GrCodeReferenceElement
    assert reference.referenceName == 'B'
    assert reference.qualifiedReferenceName == 'foo.B'
  }
}
