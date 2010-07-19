/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class GrCompletionTestWithLibrary extends GroovyCompletionTestBase {
  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_7LibraryName() + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
  }


  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completionWithLibrary/";
  }


  public void testCategoryMethod() {doBasicTest()}
  public void testCategoryProperty() {doBasicTest()}
  public void testMultipleCategories() {doBasicTest()}

}
