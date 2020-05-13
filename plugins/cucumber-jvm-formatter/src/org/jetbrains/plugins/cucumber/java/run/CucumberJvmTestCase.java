// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

public interface CucumberJvmTestCase {
  boolean isScenarioOutline();
  String getUri();
  int getScenarioOutlineLine();
  int getLine();
  String getScenarioName();
}
