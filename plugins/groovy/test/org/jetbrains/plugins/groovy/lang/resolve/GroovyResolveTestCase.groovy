/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.resolve
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
/**
 * @author ven
 */
public abstract class GroovyResolveTestCase extends LightGroovyTestCase {
  @NonNls protected static final String MARKER = "<ref>";

  @Override
  protected void setUp() {
    super.setUp();
    if (new File("$myFixture.testDataPath/${getTestName(true)}").exists()) {
      myFixture.copyDirectoryToProject(getTestName(true), "");
    }
  }

  protected <T extends PsiReference> T configureByFile(@NonNls String filePath, @Nullable String newFilePath = null, Class<T> refType = PsiReference) {
    def trimmedFilePath = StringUtil.trimStart(filePath, getTestName(true) + "/");
    VirtualFile vFile = myFixture.tempDirFixture.getFile(filePath);
    if (vFile == null) {
      vFile = myFixture.tempDirFixture.getFile(trimmedFilePath)
    }
    assertNotNull("file $filePath not found", vFile);

    String fileText;
    try {
      fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    int offset = fileText.indexOf(MARKER);
    assertTrue("'ref' marker is not found", offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length());

    if (newFilePath == null) {
      myFixture.configureByText("aaa." + vFile.extension, fileText);
    }
    else {
      myFixture.configureByText(newFilePath, fileText);
    }

    PsiReference ref = myFixture.file.findReferenceAt(offset);
    assertInstanceOf(ref, refType);
    return ref;
  }

  protected <T extends PsiReference> T configureByText(String fileName = '_a.groovy', String text, Class<T> refType = PsiReference) {
    myFixture.configureByText fileName, text
    final ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertInstanceOf(ref, refType)
    return ref
  }

  @Nullable
  protected <T extends PsiElement> T resolve(String fileName = getTestName(false) + ".groovy", Class<T> type = null) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertNotNull(ref)
    final resolved = ref.resolve()
    if (type != null) assertInstanceOf(resolved, type)
    return resolved
  }

  @Nullable
  protected <T extends PsiElement> T resolveByText(String text, Class<T> type = null) {
    final ref = configureByText(text)
    assertNotNull(ref)
    final resolved = ref.resolve()
    if (type != null) assertInstanceOf(resolved, type)
    return resolved
  }

  @Nullable
  protected GroovyResolveResult advancedResolve(String fileName) {
    PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertInstanceOf(ref, GrReferenceExpression.class);
    return ((GrReferenceExpression)ref).advancedResolve();
  }

  protected PsiClass addBaseScript() {
    myFixture.addClass("package groovy.transform; public @interface BaseScript {}")
  }

  protected PsiClass addImmutable() {
    myFixture.addClass("package groovy.lang; public @interface Immutable {}")
  }

  protected PsiClass addTupleConstructor() {
    myFixture.addClass("package groovy.transform; public @interface TupleConstructor {}")
  }

  protected PsiClass addCanonical() {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}")
  }

  protected PsiClass addInheritConstructor() {
    myFixture.addClass("package groovy.transform; public @interface InheritConstructors {}")
  }
}
