// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/// Supplies a shared substrate and the decoder of the current storage fixture
public final class KeyValueTestDataParameterResolver implements ParameterResolver {
  private static final ExtensionContext.Namespace NAMESPACE =
    ExtensionContext.Namespace.create(KeyValueTestDataParameterResolver.class);
  private static final String SUBSTRATE_KEY = "substrate";

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return parameterContext.getParameter().getType() == KeyValueTestData.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    var testInstance = extensionContext.getRequiredTestInstance();
    if (!(testInstance instanceof KeyValueStoreTestContext<?, ?, ?> testContext)) {
      throw new ParameterResolutionException("The test fixture must implement KeyValueStoreTestContext");
    }

    var substrate = extensionContext.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(
      SUBSTRATE_KEY,
      _ -> KeyValueTestData.generateSubstrate(),
      int[].class
    );
    return new KeyValueTestData<>(substrate, testContext.keyValueSubstrateDecoder());
  }
}
