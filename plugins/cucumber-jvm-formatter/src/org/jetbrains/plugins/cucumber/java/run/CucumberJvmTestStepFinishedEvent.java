// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

public interface CucumberJvmTestStepFinishedEvent {
  enum Status {PASSED, PENDING, SKIPPED, FAILED, }
  CucumberJvmTestStep getTestStep();
  Status getResult();
  Long getDuration();
  String getErrorMessage();
}
