/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy;


import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;

public class GroovyGotoImplementationTest extends JavaCodeInsightFixtureTestCase {

  public void testNoGotoImplementationOutsideSourceRoot() throws Throwable {
    final TempDirTestFixture dirFixture = new TempDirTestFixtureImpl();
    dirFixture.setUp();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      final VirtualFile outside = dirFixture.getFile("").createChildDirectory(this, "outside");
      PsiTestUtil.addContentRoot(getModule(), outside);
      VirtualFile out = outside.createChildData(this, "Outside.groovy");
      VfsUtil.saveText(out, "class Bar {}\n class Goo extends Bar {}");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    try {
      PsiFile inProject = myFixture.addFileToProject("Foo.groovy", "class <caret>Foo {}\n class Bar extends Foo {}");
      myFixture.configureFromExistingVirtualFile(inProject.getVirtualFile());

      final PsiElement[] impls = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), inProject).targets;
      assertEquals(1, impls.length);
    }
    finally {
      dirFixture.tearDown();
    }
  }

}