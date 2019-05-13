/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.psi.PsiType;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
*/
public class ParameterInfo extends VariableData {
  private int myPosition;

  public ParameterInfo(@NotNull String oldName, int position, PsiType psiType){
    super(null, psiType);
    name = oldName;
    passAsParameter = true;
    originalName = oldName;
    myPosition = position;
  }

  public int getPosition() {
    return myPosition;
  }

  public void setPosition(int position) {
    myPosition = position;
  }
}
