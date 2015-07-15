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
package org.jetbrains.plugins.groovy.geb

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Sergey Evdokimov
 */
abstract class AbstractGebLightTestCase extends LightCodeInsightFixtureTestCase {

  static descriptor = new GebProjectDescriptor()

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return descriptor;
  }


}

class GebProjectDescriptor extends DefaultLightProjectDescriptor {
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();

      def fs = JarFileSystem.instance
      modifiableModel.addRoot(fs.findFileByPath("$TestUtils.mockGroovyLibraryHome/$TestUtils.GROOVY_JAR!/"), OrderRootType.CLASSES);

      def gebJarFolder = new File(TestUtils.absoluteTestDataPath + "/mockGeb")
      for (File gebJar : gebJarFolder.listFiles()) {
        if (gebJar.name.endsWith('sources.jar')) {
          modifiableModel.addRoot(fs.findFileByPath("${gebJar.path}!/"), OrderRootType.SOURCES);
        }
        else if  (gebJar.name.endsWith(".jar")) {
          modifiableModel.addRoot(fs.findFileByPath("${gebJar.path}!/"), OrderRootType.CLASSES);
        }
      }

      modifiableModel.commit();
    }
}