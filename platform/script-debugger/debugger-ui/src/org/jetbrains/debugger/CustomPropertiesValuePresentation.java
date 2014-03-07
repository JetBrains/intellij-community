package org.jetbrains.debugger;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CustomPropertiesValuePresentation extends XValuePresentation {
  private final ObjectValue value;
  private final List<ObjectProperty> properties;

  public CustomPropertiesValuePresentation(@NotNull ObjectValue value, @NotNull List<ObjectProperty> properties) {
    this.value = value;
    this.properties = properties;
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderer.renderComment(VariableView.getClassName(value));
    renderer.renderSpecialSymbol(" {");
    boolean isFirst = true;
    for (ObjectProperty property : properties) {
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
          renderer.renderStringValue(value.getValueString());
          if (value.getValueString().length() > XValueNode.MAX_VALUE_LENGTH) {
            renderer.renderComment(XDebuggerBundle.message("node.text.ellipsis.truncated", value.getActualLength()));
          }
          break;

        case FUNCTION:
          renderer.renderComment(VariableView.trimFunctionDescription(value));
          break;

        case OBJECT:
          ObjectValue objectValue = value.asObject();
          assert objectValue != null;
          renderer.renderComment(VariableView.getClassName(objectValue));
          break;

        default:
          renderer.renderValue(value.getValueString());
      }
    }
    renderer.renderSpecialSymbol("}");
  }
}