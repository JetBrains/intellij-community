/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.sync

import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.manage.GradleJarManager
import org.jetbrains.plugins.gradle.model.gradle.GradleJar
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType
import org.jetbrains.plugins.gradle.testutil.AbstractGradleTest
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.TestGradleJarManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * @author Denis Zhdanov
 * @since 1/17/13 4:40 PM
 */
class GradleMovedJarsPostProcessorTest extends AbstractGradleTest {

  @Test
  void "moved jar matched"() {
    init(
      gradle: {
        project {
          module {
            dependencies {
              library('lib1', bin: ['repo1/jar1', 'repo1/jar2'], src: [ 'repo1/zip1', 'repo1/zip2']) }}}},
      intellij: {
        project {
          module {
            dependencies {
              library('lib1', bin: ['repo2/jar1', 'repo2/jar2'], src: [ 'repo2/zip1', 'repo2/zip2']) }}}}
    )

    checkChanges { }

    def gradleLibrary = gradle.libraries['lib1'] as GradleLibrary
    def ideLibrary = intellij.libraries['lib1'] as Library
    def expectedImported = [
      new GradleJar(GradleUtil.toCanonicalPath('repo1/jar1'), LibraryPathType.BINARY, null, gradleLibrary),
      new GradleJar(GradleUtil.toCanonicalPath('repo1/jar2'), LibraryPathType.BINARY, null, gradleLibrary),
      new GradleJar(GradleUtil.toCanonicalPath('repo1/zip1'), LibraryPathType.SOURCE, null, gradleLibrary),
      new GradleJar(GradleUtil.toCanonicalPath('repo1/zip2'), LibraryPathType.SOURCE, null, gradleLibrary),
    ]
    
    def expectedRemoved = [
      new GradleJar(GradleUtil.toCanonicalPath('repo2/jar1'), LibraryPathType.BINARY, ideLibrary, null),
      new GradleJar(GradleUtil.toCanonicalPath('repo2/jar2'), LibraryPathType.BINARY, ideLibrary, null),
      new GradleJar(GradleUtil.toCanonicalPath('repo2/zip1'), LibraryPathType.SOURCE, ideLibrary, null),
      new GradleJar(GradleUtil.toCanonicalPath('repo2/zip2'), LibraryPathType.SOURCE, ideLibrary, null),
    ]
    TestGradleJarManager jarManager = container.getComponentInstance(GradleJarManager)
    Assert.assertEquals(expectedImported.toSet(), jarManager.importedJars.toSet())
    Assert.assertEquals(expectedRemoved.toSet(), jarManager.removedJars.toSet())
  }
}
