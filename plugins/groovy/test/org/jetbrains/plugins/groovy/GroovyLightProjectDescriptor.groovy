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
package org.jetbrains.plugins.groovy

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar
import static org.jetbrains.plugins.groovy.util.TestUtils.*

/**
 * @author Max Medvedev
 */
class GroovyLightProjectDescriptor extends DefaultLightProjectDescriptor {
  public static final GroovyLightProjectDescriptor GROOVY_2_1 = new GroovyLightProjectDescriptor(mockGroovy2_1LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_2_2 = new GroovyLightProjectDescriptor(mockGroovy2_2LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_2_3 = new GroovyLightProjectDescriptor(mockGroovy2_3LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_2_3_9 = new GroovyLightProjectDescriptor(getBundledGroovyJar() as String)

  private final String myLibPath

  protected GroovyLightProjectDescriptor(String libPath) {
    myLibPath = libPath
  }

  @Override
  public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
    final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel;
    final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath("${myLibPath}!/");
    assert groovyJar != null;
    modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
