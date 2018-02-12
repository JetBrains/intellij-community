/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GrTurnRefsToSuperTest extends MultiFileTestCase {
  public void testNoReturnType() {
    doTest("ClassB", "ClassB1", false);
  }

  private void doTest(@NonNls final String className, @NonNls final String superClassName, final boolean replaceInstanceOf) {
    doTest((rootDir, rootAfter) -> this.performAction(className, superClassName, replaceInstanceOf), true);
  }

  @NotNull
  @Override
  public String getTestRoot() {
    return "/refactoring/turnRefsToSuper/";
  }

  @Override
  protected String getTestDataPath() {
    return TestUtils.getAbsoluteTestDataPath();
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) {
    final PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
    assertNotNull("Class " + className + " not found", aClass);
    PsiClass superClass = myJavaFacade.findClass(superClassName, GlobalSearchScope.allScope(myProject));
    assertNotNull("Class " + superClassName + " not found", superClass);

    new TurnRefsToSuperProcessor(myProject, aClass, superClass, replaceInstanceOf).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
