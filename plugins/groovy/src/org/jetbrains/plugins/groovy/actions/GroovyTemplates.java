// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @NonNls String GROOVY_RECORD = "Groovy Record.groovy";

  @NonNls String GROOVY_SCRIPT = "Groovy Script.groovy";
  @NonNls String GROOVY_DSL_SCRIPT = "Groovy DSL Script.gdsl";

  @NonNls String GANT_SCRIPT = "Gant Script.gant";

  @NonNls String GROOVY_FROM_USAGE_METHOD_BODY = "Groovy New Method Body.groovy";
  @NonNls String GROOVY_JUNIT_TEST_METHOD_GROOVY = "Groovy JUnit Test Method.groovy";
  @NonNls String GROOVY_JUNIT_SET_UP_METHOD_GROOVY = "Groovy JUnit SetUp Method.groovy";
  @NonNls String GROOVY_JUNIT_TEAR_DOWN_METHOD_GROOVY = "Groovy JUnit TearDown Method.groovy";
  @NonNls String GROOVY_JUNIT_TEST_CASE_GROOVY = "Groovy JUnit Test Case.groovy";
}
