/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrParameterInfo {
  private String myName = "";
  private String myDefaultValue = "";
  private String myType = "";
  private String myDefaultInitializer = "";
  private final int myPosition;

  public GrParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    myName = parameter.getName();
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      myType = type.getCanonicalText();
    }
    final GrExpression defaultInitializer = parameter.getDefaultInitializer();
    if (defaultInitializer != null) {
      myDefaultInitializer = defaultInitializer.getText();
    }
  }


  public GrParameterInfo() {
    this.myPosition = -1;
  }

  public String getName() {
    return myName;
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }

  public String getType() {
    return myType;
  }

  public String getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setDefaultValue(String defaultValue) {
    myDefaultValue = defaultValue;
  }

  public void setType(String type) {
    myType = type;
  }

  public void setDefaultInitializer(String defaultInitializer) {
    myDefaultInitializer = defaultInitializer;
  }

  public int getPosition() {
    return myPosition;
  }
}
