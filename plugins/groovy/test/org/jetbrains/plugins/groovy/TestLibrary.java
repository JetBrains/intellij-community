// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

public interface TestLibrary {

  void addTo(@NotNull Module module, @NotNull ModifiableRootModel model);

  @NotNull
  default TestLibrary plus(@NotNull TestLibrary library) {
    return new CompoundTestLibrary(this, library);
  }
}
