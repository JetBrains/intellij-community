// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class LibraryLightProjectDescriptor extends DefaultLightProjectDescriptor {

  private final TestLibrary myLibrary

  LibraryLightProjectDescriptor(TestLibrary library) {
    myLibrary = library
  }

  @Override
  void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
    super.configureModule(module, model, contentEntry)
    myLibrary.addTo(module, model)
  }
}
