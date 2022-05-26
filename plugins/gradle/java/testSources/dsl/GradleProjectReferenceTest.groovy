// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.model.psi.impl.TargetsKt
import com.intellij.openapi.application.ApplicationManager
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.service.resolve.GradleSubprojectSymbol
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureFactory
import org.junit.Test

import static org.jetbrains.plugins.gradle.testFramework.GradleFileTestUtil.withSettingsFile

@CompileStatic
class GradleProjectReferenceTest extends GradleHighlightingLightTestCase {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return GradleTestFixtureFactory.fixtureFactory
      .createGradleTestFixture("GradleProjectReferenceTest", gradleVersion) {
        withSettingsFile(it) {
          it.setProjectName("GradleProjectReferenceTest")
          it.include("child")
          it.include("child:foo")
          it.include("child:foo:bar")
          it.include("child:foo:baz")
          it.include("child:bar")
          it.include("child:bar:foo")
        }
      }
  }

  @Test
  void 'test rename child'() {
    testRename '''\
// :child:foo:bar
project(':child')
project(':child:foo')
project(':<caret>child:foo:bar')
project(':child:foo:baz')
project(':child:bar')
project(':child:bar:foo')
// :child:foo:bar
// :child:foox:bar
// :childx:foo:bar
println ":child:foo:bar"
println ":child:foox:bar"
println ":xchild:foox:bar"
''', '''\
// :xxx:foo:bar
project(':xxx')
project(':xxx:foo')
project(':<caret>xxx:foo:bar')
project(':xxx:foo:baz')
project(':xxx:bar')
project(':xxx:bar:foo')
// :xxx:foo:bar
// :xxx:foox:bar
// :xxxx:foo:bar
println ":xxx:foo:bar"
println ":xxx:foox:bar"
println ":xchild:foox:bar"
'''
  }

  @Test
  void 'test rename grand child'() {
    testRename '''\
// :child:foo:bar
project(':child')
project(':child:<caret>foo')
project(':child:foo:bar')
project(':child:foo:baz')
project(':child:bar')
project(':child:bar:foo')
// :child:foo:bar
// :child:foox:bar
// :childx:foo:bar
println ":child:foo:bar"
println ":child:foox:bar"
println ":xchild:foo:bar"
''', '''\
// :child:xxx:bar
project(':child')
project(':child:<caret>xxx')
project(':child:xxx:bar')
project(':child:xxx:baz')
project(':child:bar')
project(':child:bar:foo')
// :child:xxx:bar
// :child:xxxx:bar
// :childx:foo:bar
println ":child:xxx:bar"
println ":child:xxxx:bar"
println ":xchild:foo:bar"
'''
  }

  private void testRename(String before, String after) {
    ApplicationManager.application.invokeAndWait {
      updateProjectFile before
      def symbol = TargetsKt.targetSymbols(fixture.file, fixture.caretOffset)[0] as GradleSubprojectSymbol
      fixture.renameTarget(symbol, "xxx")
      fixture.checkResult after
    }
  }
}
