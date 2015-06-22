package com.intellij.testFramework;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.*;
import org.jetbrains.annotations.NotNull;

public class MockSchemesManagerFactory extends SchemesManagerFactory {
  private static final SchemesManager EMPTY = new EmptySchemesManager();

  @NotNull
  @Override
  public <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T, E> createSchemesManager(@NotNull String fileSpec,
                                                                                                      @NotNull SchemeProcessor<E> processor,
                                                                                                      @NotNull RoamingType roamingType) {
    //noinspection unchecked
    return EMPTY;
  }
}
