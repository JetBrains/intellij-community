/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.generate.element;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;

import java.util.List;

public class GenerationHelper {

  //used in generate equals/hashCode
  @SuppressWarnings("unused")
  public static String getUniqueLocalVarName(String base, List<Element> elements, CodeStyleSettings settings) {
    base = settings.LOCAL_VARIABLE_NAME_PREFIX + base;
    String id = base;
    int index = 0;
    while (true) {
      if (index > 0) {
        id = base + index;
      }
      index++;
      boolean anyEqual = false;
      for (Element equalsField : elements) {
        if (id.equals(equalsField.getName())) {
          anyEqual = true;
          break;
        }
      }
      if (!anyEqual) break;
    }


    return id;
  }

  public static String getParamName(FieldElement fieldElement, Project project) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    return codeStyleManager.propertyNameToVariableName(getPropertyName(fieldElement, project), VariableKind.PARAMETER);
  }

  public static String getPropertyName(FieldElement fieldElement, Project project) {
    String name = fieldElement.getName();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    return codeStyleManager.variableNameToPropertyName(name, fieldElement.isModifierStatic() ? VariableKind.STATIC_FIELD : VariableKind.FIELD);
  }
}
