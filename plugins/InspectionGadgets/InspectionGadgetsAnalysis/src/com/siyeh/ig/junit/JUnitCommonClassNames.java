/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.junit;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class JUnitCommonClassNames {
  public static final String ORG_JUNIT_ASSERT = "org.junit.Assert";
  public static final String ORG_JUNIT_ASSUME = "org.junit.Assume";
  public static final String JUNIT_FRAMEWORK_ASSERT = "junit.framework.Assert";
  public static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
  public static final String ORG_JUNIT_JUPITER_API_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";
  public static final String JUNIT_FRAMEWORK_TEST_CASE = "junit.framework.TestCase";
  public static final String ORG_HAMCREST_MATCHER_ASSERT = "org.hamcrest.MatcherAssert";
  public static final String ORG_JUNIT_TEST = "org.junit.Test";
  public static final String ORG_JUNIT_RULE = "org.junit.Rule";
  public static final String ORG_JUNIT_CLASS_RULE = "org.junit.ClassRule";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE = "org.junit.jupiter.params.provider.MethodSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE = "org.junit.jupiter.params.provider.ValueSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE = "org.junit.jupiter.params.provider.EnumSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE = "org.junit.jupiter.params.provider.CsvSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE = "org.junit.jupiter.params.provider.CsvFileSource";
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE = "org.junit.jupiter.params.provider.ArgumentsSource";
  public static final Collection<String> SOURCE_ANNOTATIONS = Collections.unmodifiableList(Arrays.asList(
    ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE,
    ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE,
    ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE,
    ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE,
    ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE
  ));
  public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS = "org.junit.jupiter.params.provider.Arguments";
  public static final String ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH = "org.junit.jupiter.params.converter.ConvertWith";
  public static final String ORG_JUNIT_JUPITER_API_TEST = "org.junit.jupiter.api.Test";
  public static final String ORG_JUNIT_JUPITER_API_REPEATED_TEST = "org.junit.jupiter.api.RepeatedTest";
  public static final String ORG_JUNIT_JUPITER_API_REPETITION_INFO = "org.junit.jupiter.api.RepetitionInfo";
  public static final String ORG_JUNIT_JUPITER_API_TEST_INFO = "org.junit.jupiter.api.TestInfo";
  public static final String ORG_JUNIT_JUPITER_API_TEST_REPORTER = "org.junit.jupiter.api.TestReporter";
  public static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
  public static final String ORG_JUNIT_JUPITER_API_TEST_INSTANCE = "org.junit.jupiter.api.TestInstance";
  public static final String ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE = "org.junit.platform.engine.TestEngine";
}
