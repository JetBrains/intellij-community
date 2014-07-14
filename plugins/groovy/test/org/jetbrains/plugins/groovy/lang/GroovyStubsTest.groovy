/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant

/**
 * @author peter
 */
class GroovyStubsTest extends LightCodeInsightFixtureTestCase {

  public void testEnumConstant() {
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

  public void testStubIndexMismatch() {
    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.groovy");
    final Project project = myFixture.getProject();
    PsiFileImpl fooFile = (PsiFileImpl) PsiManager.getInstance(project).findFile(vFile);
    final Document fooDocument = fooFile.getViewProvider().getDocument();
    assert !JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))
    new WriteCommandAction.Simple(project, fooFile) {
      public void run() {
        fooDocument.setText("class Fooxx {}");
      }
    }.execute();
    PsiDocumentManager.getInstance(project).commitDocument(fooDocument);
    fooFile.setTreeElementPointer(null);
    DumbServiceImpl.getInstance(project).setDumb(true);
    try {
      assertOneElement(((GroovyFile) fooFile).classes);
      assertFalse(fooFile.isContentsLoaded());
    }
    finally {
      DumbServiceImpl.getInstance(project).setDumb(false);
    }
    assert JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))
  }

}
