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

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableSettings;

/**
 * Created by Max Medvedev on 12/1/13
 */
public class MockSettings implements GroovyIntroduceVariableSettings {
  private final boolean myFinal;
  private final String myName;
  private final boolean myAllOccurrences;
  private final PsiType myType;

  public MockSettings(final boolean isFinal, final String name, PsiType type, boolean allOccurrences) {
    myFinal = isFinal;
    myName = name;
    myType = type;
    myAllOccurrences = allOccurrences;
  }

  @Override
  public boolean isDeclareFinal() {
    return myFinal;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean replaceAllOccurrences() {
    return myAllOccurrences;
  }

  @Override
  public PsiType getSelectedType() {
    return myType;
  }
}
