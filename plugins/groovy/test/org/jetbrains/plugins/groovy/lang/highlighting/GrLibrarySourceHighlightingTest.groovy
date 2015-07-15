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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils

class GrLibrarySourceHighlightingTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final absoluteBasePath = "${TestUtils.absoluteTestDataPath}${basePath}"
      final lib = JarFileSystem.instance.refreshAndFindFileByPath("$absoluteBasePath/some-library.jar!/")
      final src = LocalFileSystem.instance.refreshAndFindFileByPath("$absoluteBasePath/src/")

      final modifiableModel = model.moduleLibraryTable.createLibrary("some-library").modifiableModel;
      modifiableModel.addRoot lib, OrderRootType.CLASSES
      modifiableModel.addRoot src, OrderRootType.SOURCES
      modifiableModel.commit();
    }
  }

  final String basePath = "highlighting/librarySources"

  void "test no errors trait highlighting"() {
    def clazz = JavaPsiFacade.getInstance(project).findClass("somepackage.CC", GlobalSearchScope.moduleWithLibrariesScope(myModule))
    def psiFile = (clazz as ClsClassImpl).sourceMirrorClass.containingFile
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.testHighlighting()
  }
}
