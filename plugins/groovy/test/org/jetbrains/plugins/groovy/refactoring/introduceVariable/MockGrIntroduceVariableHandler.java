// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableSettings;

public class MockGrIntroduceVariableHandler extends GrIntroduceVariableHandler {
  private final MockSettings mySettings;

  public MockGrIntroduceVariableHandler(MockSettings settings) {
    mySettings = settings;
  }

  @Override
  protected @Nullable GroovyIntroduceVariableSettings showDialog(@NotNull GrIntroduceContext context) {
    return mySettings;
  }
}
