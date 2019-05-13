// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.highlighting

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.runners.Parameterized

abstract class GradleHighlightingBaseTest extends GradleImportingTestCase {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  static Collection<Object[]> data() {
    return [[BASE_GRADLE_VERSION].toArray()]
  }

  @NotNull
  JavaCodeInsightTestFixture fixture

  @Override
  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
    fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
    ((JavaCodeInsightTestFixtureImpl)fixture).virtualFileFilter = null
    fixture.setUp()
  }

  void testHighlighting(@NotNull String text) {
    VirtualFile file = createProjectSubFile "build.gradle", text

    importProject()
    EdtTestUtil.runInEdtAndWait {
      fixture.testHighlightingAllFiles(true, false, true, file)
    }
  }

  @Nullable
  PsiElement testResolve(@NotNull String text, @NotNull String substring) {
    VirtualFile vFile = createProjectSubFile "build.gradle", text
    importProject()
    int offset = text.indexOf(substring) + 1
    return ReadAction.compute {
      def file = fixture.psiManager.findFile(vFile)
      PsiReference reference = file.findReferenceAt(offset)
      assert reference != null
      return reference.resolve()
    }
  }

  void doTest(@NotNull String text, Closure test) {
    VirtualFile vFile = createProjectSubFile 'build.gradle', text
    fixture.configureFromExistingVirtualFile(vFile)
    importProject()
    ReadAction.run(test)
  }

  @Override
  void tearDownFixtures() {
    fixture.tearDown()
  }
}

