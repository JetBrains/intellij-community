// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.stream.Stream;

/**
 * Arguments provider to be registered as
 * <pre><code>
 *   &#064;ArgumentsSource(FileBasedArgumentProvider.class)
 *   &#064;ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
 *   public void parameterized(String fileName) {}
 * </code></pre>
 * 
 * Expected that containing test implements {@link FileBasedTestCaseHelperEx}
 * and test data is located under {@code PathManagerEx.getTestDataPath()}.
 */
public class FileBasedArgumentProvider implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
    try {
      return LightPlatformCodeInsightTestCase.params(context.getRequiredTestClass()).stream().map(obj -> Arguments.of(obj[0]));
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}