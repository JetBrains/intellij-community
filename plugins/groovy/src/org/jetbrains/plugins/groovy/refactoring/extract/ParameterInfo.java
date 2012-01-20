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
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
*/
public class ParameterInfo {
  private final String myOldName;
  private String myNewName;
  private int myPosition;
  private boolean myPassAsParameter = true;
  private PsiType myType;

  public ParameterInfo(@NotNull String oldName, int position, PsiType psiType){
    myOldName = oldName;
    setType(psiType);
    myNewName = myOldName;
    myPosition = position;
  }

  public String getOldName() {
    return myOldName;
  }

  public int getPosition() {
    return myPosition;
  }

  public void setPosition(int position) {
    myPosition = position;
  }

  public String getName() {
    return myNewName;
  }

  public PsiType getType() {
    return myType;
  }

  public void setNewName(String newName) {
    myNewName = newName;
  }

  public boolean passAsParameter() {
    return myPassAsParameter;
  }

  public void setPassAsParameter(boolean passAsParameter) {
    myPassAsParameter = passAsParameter;
  }

  public void setType(PsiType type) {
    myType = type;
  }
}
