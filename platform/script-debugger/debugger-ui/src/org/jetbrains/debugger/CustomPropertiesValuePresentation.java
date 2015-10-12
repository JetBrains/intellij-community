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
package org.jetbrains.debugger;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.values.ObjectValue;
import org.jetbrains.debugger.values.StringValue;
import org.jetbrains.debugger.values.Value;

import java.util.List;

public class CustomPropertiesValuePresentation extends XValuePresentation {
  private final ObjectValue value;
  private final List<Variable> properties;

  public CustomPropertiesValuePresentation(@NotNull ObjectValue value, @NotNull List<Variable> properties) {
    this.value = value;
    this.properties = properties;
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderer.renderComment(VariableViewKt.getObjectValueDescription(value));
    renderer.renderSpecialSymbol(" {");
    boolean isFirst = true;
    for (Variable property : properties) {
      if (isFirst) {
        isFirst = false;
      }
      else {
        renderer.renderSpecialSymbol(", ");
      }

      renderer.renderValue(property.getName());
      renderer.renderSpecialSymbol(": ");

      Value value = property.getValue();
      assert value != null;

      switch (value.getType()) {
        case BOOLEAN:
        case NULL:
        case UNDEFINED:
          renderer.renderKeywordValue(value.getValueString());
          break;

        case NUMBER:
          renderer.renderNumericValue(value.getValueString());
          break;

        case STRING:
          String string = value.getValueString();
          renderer.renderStringValue(string, "\"\\", XValueNode.MAX_VALUE_LENGTH);
          int actualStringLength = value instanceof StringValue ? ((StringValue)value).getLength() : string.length();
          if (actualStringLength > XValueNode.MAX_VALUE_LENGTH) {
            renderer.renderComment(XDebuggerBundle.message("node.text.ellipsis.truncated", actualStringLength));
          }
          break;

        case FUNCTION:
          renderer.renderComment(VariableViewKt.trimFunctionDescription(value));
          break;

        case OBJECT:
          renderer.renderComment(VariableViewKt.getObjectValueDescription((ObjectValue)value));
          break;

        default:
          renderer.renderValue(value.getValueString());
      }
    }
    renderer.renderSpecialSymbol("}");
  }
}