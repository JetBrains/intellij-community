// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.junit;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

@NonNls
public final class JUnitCommonClassNames {
  public static final String ORG_JUNIT_ASSERT = "org.junit.Assert";
  public static final String ORG_JUNIT_ASSUME = "org.junit.Assume";
  public static final String JUNIT_FRAMEWORK_ASSERT = "junit.framework.Assert";
  public static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
  public static final String ORG_JUNIT_JUPITER_API_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";
  public static final String JUNIT_FRAMEWORK_TEST_CASE = "junit.framework.TestCase";
  public static final String ORG_HAMCREST_MATCHER_ASSERT = "org.hamcrest.MatcherAssert";
  public static final String ORG_JUNIT_TEST = "org.junit.Test";
  public static final String ORG_JUNIT_IGNORE = "org.junit.Ignore";
  public static final String ORG_JUNIT_RULE = "org.junit.Rule";
  public static final String ORG_JUNIT_CLASS_RULE = "org.junit.ClassRule";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE = "org.junit.jupiter.params.provider.MethodSource";
  public static final String ORG_JUNIT_JUPITER_CONDITION_PROVIDER_ENABLED_IF = "org.junit.jupiter.api.condition.EnabledIf";
  public static final String ORG_JUNIT_JUPITER_CONDITION_PROVIDER_DISABLED_IF = "org.junit.jupiter.api.condition.DisabledIf";
  public static final String ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE = "org.junit.jupiter.params.provider.ValueSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE = "org.junit.jupiter.params.provider.EnumSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE_SHORT = "EnumSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE = "org.junit.jupiter.params.provider.NullSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE = "org.junit.jupiter.params.provider.EmptySource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE = "org.junit.jupiter.params.provider.NullAndEmptySource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE = "org.junit.jupiter.params.provider.CsvSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE = "org.junit.jupiter.params.provider.CsvFileSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE = "org.junit.jupiter.params.provider.ArgumentsSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCES = "org.junit.jupiter.params.provider.ArgumentsSources";
  public static final Collection<String> SOURCE_ANNOTATIONS = ContainerUtil
    .immutableList(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE, ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE,
                   ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE,
                   ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE,
                   ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCES,
                   ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE,
                   ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE,
                   ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE);
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS = "org.junit.jupiter.params.provider.Arguments";
  public static final String ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH = "org.junit.jupiter.params.converter.ConvertWith";
  public static final String ORG_JUNIT_JUPITER_API_TEST = "org.junit.jupiter.api.Test";
  public static final String ORG_JUNIT_JUPITER_API_NESTED = "org.junit.jupiter.api.Nested";
  public static final String ORG_JUNIT_JUPITER_API_REPEATED_TEST = "org.junit.jupiter.api.RepeatedTest";
  public static final String ORG_JUNIT_JUPITER_API_REPETITION_INFO = "org.junit.jupiter.api.RepetitionInfo";
  public static final String ORG_JUNIT_JUPITER_API_TEST_INFO = "org.junit.jupiter.api.TestInfo";
  public static final String ORG_JUNIT_JUPITER_API_TEST_REPORTER = "org.junit.jupiter.api.TestReporter";
  public static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
  public static final String ORG_JUNIT_JUPITER_API_TEST_INSTANCE = "org.junit.jupiter.api.TestInstance";
  public static final String ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE = "org.junit.platform.engine.TestEngine";
  public static final String ORG_JUNIT_PLATFORM_ENGINE = "org.junit.platform.engine";
  public static final String ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION = "org.junit.jupiter.api.extension.RegisterExtension";
  public static final String ORG_JUNIT_JUPITER_API_EXTENSION = "org.junit.jupiter.api.extension.Extension";
}
