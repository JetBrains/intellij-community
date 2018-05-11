// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar
import static org.jetbrains.plugins.groovy.util.TestUtils.*

/**
 * @author Max Medvedev
 */
@CompileStatic
class GroovyLightProjectDescriptor extends DefaultLightProjectDescriptor {
  public static final GroovyLightProjectDescriptor GROOVY_2_1 = new GroovyLightProjectDescriptor(mockGroovy2_1LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_2_2 = new GroovyLightProjectDescriptor(mockGroovy2_2LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_2_3 = new GroovyLightProjectDescriptor(mockGroovy2_3LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_3_0 = new GroovyLightProjectDescriptor(mockGroovy3_0LibraryName)
  public static final GroovyLightProjectDescriptor GROOVY_LATEST = new GroovyLightProjectDescriptor(bundledGroovyJar as String)
  public static final GroovyLightProjectDescriptor GROOVY_LATEST_REAL_JDK = new GroovyLightProjectDescriptor(bundledGroovyJar as String) {
    @Override
    Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false)
    }
  }
  private final String myLibPath

  GroovyLightProjectDescriptor(String libPath) {
    myLibPath = libPath
  }

  @Override
  void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
    super.configureModule(module, model, contentEntry)
    final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel
    final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath("${myLibPath}!/")
    assert groovyJar != null
    modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES)
    modifiableModel.commit()
  }
}
