package org.jetbrains.plugins.groovy;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

public final class CompoundTestLibrary implements TestLibrary {
  public CompoundTestLibrary(TestLibrary... libraries) {
    assert libraries.length > 0;
    myLibraries = libraries;
  }

  @Override
  public void addTo(@NotNull Module module, @NotNull ModifiableRootModel model) {
    for (TestLibrary library : myLibraries) {
      library.addTo(module, model);
    }
  }

  private final TestLibrary[] myLibraries;
}
