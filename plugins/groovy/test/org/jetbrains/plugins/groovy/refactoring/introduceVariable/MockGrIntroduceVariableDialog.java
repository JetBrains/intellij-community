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
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator;

/**
 * Created by Max Medvedev on 12/1/13
 */
public class MockGrIntroduceVariableDialog extends GroovyIntroduceVariableDialog {
  private final MockSettings mySettings;

  public MockGrIntroduceVariableDialog(GrIntroduceContext context, GroovyVariableValidator validator, MockSettings settings) {
    super(context, validator);
    mySettings = settings;
  }

  @Override
  public void show() {
    close(0);
  }

  @Override
  public MockSettings getSettings() {
    return mySettings;
  }

  @Override
  public boolean isOK() {
    return true;
  }
}
