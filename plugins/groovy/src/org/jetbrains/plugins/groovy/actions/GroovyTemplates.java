/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions;

import org.jetbrains.annotations.NonNls;

/**
 * @author Max Medvedev
 */
public interface GroovyTemplates {
  @NonNls String GROOVY_CLASS = "Groovy Class.groovy";
  @NonNls String GROOVY_INTERFACE = "Groovy Interface.groovy";
  @NonNls String GROOVY_TRAIT = "Groovy Trait.groovy";
  @NonNls String GROOVY_ENUM = "Groovy Enum.groovy";
  @NonNls String GROOVY_ANNOTATION = "Groovy Annotation.groovy";

  @NonNls String GROOVY_SCRIPT = "Groovy Script.groovy";
  @NonNls String GROOVY_DSL_SCRIPT = "Groovy DSL Script.gdsl";

  @NonNls String GANT_SCRIPT = "Gant Script.gant";

  @NonNls String GROOVY_FROM_USAGE_METHOD_BODY = "Groovy New Method Body.groovy";
  @NonNls String GROOVY_JUNIT_TEST_METHOD_GROOVY = "Groovy JUnit Test Method.groovy";
  @NonNls String GROOVY_JUNIT_SET_UP_METHOD_GROOVY = "Groovy JUnit SetUp Method.groovy";
  @NonNls String GROOVY_JUNIT_TEAR_DOWN_METHOD_GROOVY = "Groovy JUnit TearDown Method.groovy";
  @NonNls String GROOVY_JUNIT_TEST_CASE_GROOVY = "Groovy JUnit Test Case.groovy";
}
