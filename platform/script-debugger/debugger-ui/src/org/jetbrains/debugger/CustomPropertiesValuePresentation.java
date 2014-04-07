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
    renderer.renderComment(VariableView.getClassName(value));
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
          renderer.renderComment(VariableView.trimFunctionDescription(value));
          break;

        case OBJECT:
          ObjectValue objectValue = (ObjectValue)value;
          renderer.renderComment(VariableView.getClassName(objectValue));
          break;

        default:
          renderer.renderValue(value.getValueString());
      }
    }
    renderer.renderSpecialSymbol("}");
  }
}