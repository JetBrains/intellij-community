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
package org.jetbrains.plugins.groovy.codeInspection.dependencies

import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyVisitorFactory
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GrDependencyVisitorTest extends LightGroovyTestCase {

  void test() {
    def file = myFixture.addFileToProject("C.groovy", "import groovy.util.ConfigObject\nclass C { }")

    def deps = []
    DependenciesBuilder.analyzeFileDependencies(file, { _, dep -> deps << dep }, DependencyVisitorFactory.VisitorOptions.SKIP_IMPORTS)
    assert deps.size() == 0

    DependenciesBuilder.analyzeFileDependencies(file, { _, dep -> deps << dep }, DependencyVisitorFactory.VisitorOptions.INCLUDE_IMPORTS)
    assert deps.size() == 3
  }
}
