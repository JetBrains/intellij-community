// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
final class CompoundTestLibrary implements TestLibrary {

  private final TestLibrary[] myLibraries

  CompoundTestLibrary(TestLibrary... libraries) {
    assert libraries.length > 0
    myLibraries = libraries
  }

  @Override
  void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
    for (library in myLibraries) {
      library.addTo(module, model)
    }
  }
}
