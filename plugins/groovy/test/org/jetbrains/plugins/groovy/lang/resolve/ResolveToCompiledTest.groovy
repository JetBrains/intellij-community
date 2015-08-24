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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar

@CompileStatic
class ResolveToCompiledTest extends GroovyResolveTestCase {

  final String basePath = "resolve/"

  final LightProjectDescriptor projectDescriptor = new GroovyLightProjectDescriptor(getBundledGroovyJar() as String) {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addLibrary(module, model, "some-library", "${TestUtils.absoluteTestDataPath}/lib", 'some-library.jar');
    }
  }

  void 'test resolve implemented trait method()'() {
  }
}
