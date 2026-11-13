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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrTurnRefsToSuperTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy =
    groovyFixture(project, new DefaultLightProjectDescriptor(), "refactoring/turnRefsToSuper/");
  private final TestFixture<String> lowerTestName = testNameFixture(true);

  @Test
  public void testNoReturnType() {
    doTest("ClassB", "ClassB1", false);
  }

  /** Copies `before`, runs the refactoring, and compares the result with `after`, as the JUnit 3 base `MultiFileTestCase` does. */
  private void doTest(@NonNls final String className, @NonNls final String superClassName, final boolean replaceInstanceOf) {
    String path = groovy.get().getTestDataPath() + lowerTestName.get();
    VirtualFile rootDir = groovy.get().copyDirectoryToProject(lowerTestName.get() + "/before", "");
    VirtualFile rootAfter = StandardFileSystems.local().findFileByPath(path + "/after");
    EdtTestUtil.runInEdtAndWait(() -> {
      performAction(className, superClassName, replaceInstanceOf);
      Project project = groovy.get().getProject();
      WriteCommandAction.runWriteCommandAction(project, () -> PostprocessReformattingAspect.getInstance(project).doPostponedFormatting());
      FileDocumentManager.getInstance().saveAllDocuments();
      try {
        PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) {
    Project project = groovy.get().getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass aClass = facade.findClass(className, GlobalSearchScope.allScope(project));
    assertNotNull(aClass, "Class " + className + " not found");
    PsiClass superClass = facade.findClass(superClassName, GlobalSearchScope.allScope(project));
    assertNotNull(superClass, "Class " + superClassName + " not found");

    new TurnRefsToSuperProcessor(project, aClass, superClass, replaceInstanceOf).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
