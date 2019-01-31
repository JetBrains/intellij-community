// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

/**
 * @author Max Medvedev
 */
@CompileStatic
class GroovyLightProjectDescriptor extends DefaultLightProjectDescriptor {
  public static final LightProjectDescriptor GROOVY_LATEST = GroovyProjectDescriptors.GROOVY_LATEST
  public static final LightProjectDescriptor GROOVY_LATEST_REAL_JDK = GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK
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
