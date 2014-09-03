package com.intellij.testFramework;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.*;
import org.jetbrains.annotations.NotNull;

public class MockSchemesManagerFactory extends SchemesManagerFactory {
  @Override
  public <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T, E> createSchemesManager(@NotNull String fileSpec,
                                                                                                      @NotNull SchemeProcessor<E> processor,
                                                                                                      @NotNull RoamingType roamingType) {
    //noinspection unchecked
    return SchemesManager.EMPTY;
  }

  @Override
  public void updateConfigFilesFromStreamProviders() {

  }
}
