package org.jetbrains.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XKeywordValuePresentation;
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation;
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.*;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class VariableView extends XNamedValue implements VariableContext {
  private static final Pattern ARRAY_DESCRIPTION_PATTERN = Pattern.compile("^[a-zA-Z]+\\[\\d+\\]$");
  private static final class ArrayPresentation extends XValuePresentation {
    private final String length;

    private ArrayPresentation(int length) {
      this.length = Integer.toString(length);
    }

    @Override
    public void renderValue(@NotNull XValueTextRenderer renderer) {
      renderer.renderSpecialSymbol("Array[");
      renderer.renderSpecialSymbol(length);
      renderer.renderSpecialSymbol("]");
    }
  }

  private final VariableContext context;

  private final Variable variable;

  private volatile Value value;

  private volatile List<Variable> remainingChildren;
  private volatile int remainingChildrenOffset;
  private volatile int childrenModificationStamp;

  public VariableView(@NotNull VariableContext context, @NotNull Variable variable) {
    super(context.getMemberFilter().normalizeMemberName(variable));

    this.context = context;
    this.variable = variable;
  }

  @NotNull
  public static String getClassName(@NotNull ObjectValue value) {
    String className = value.getClassName();
    return StringUtil.isEmpty(className) ? "Object" : className;
  }

  public static void setObjectPresentation(@NotNull ObjectValue value, @NotNull Icon icon, @NotNull XValueNode node) {
    node.setPresentation(icon, new ObjectValuePresentation(getClassName(value)), value.hasProperties() != ThreeState.NO);
  }

  public static void setArrayPresentation(@NotNull Value value, @NotNull VariableContext context, @NotNull final Icon icon, @NotNull XValueNode node) {
    assert value.getType() == ValueType.ARRAY;

    if (value instanceof ArrayValue) {
      int length = ((ArrayValue)value).getLength();
      node.setPresentation(icon, new ArrayPresentation(length), length > 0);
      return;
    }

    String valueString = value.getValueString();
    // only WIP reports normal description
    if (valueString != null && valueString.endsWith("]") && ARRAY_DESCRIPTION_PATTERN.matcher(valueString).find()) {
      node.setPresentation(icon, null, valueString, true);
    }
    else {
      ObsolescentAsyncResults.consume(context.getEvaluateContext().evaluate("a.length", Collections.<String, EvaluateContextAdditionalParameter>singletonMap("a", value)), node,
                                      new PairConsumer<Value, XValueNode>() {
                                        @Override
                                        public void consume(Value lengthValue, XValueNode node) {
                                          node.setPresentation(icon, null, "Array[" + lengthValue.getValueString() + ']', true);
                                        }
                                      });
    }
  }

  @NotNull
  public static Icon getIcon(@NotNull Value value) {
    ValueType type = value.getType();
    switch (type) {
      case FUNCTION:
        return AllIcons.Nodes.Function;
      case ARRAY:
        return AllIcons.Debugger.Db_array;
      default:
        return type.isObjectType() ? AllIcons.Debugger.Value : AllIcons.Debugger.Db_primitive;
    }
  }

  @Override
  public boolean watchableAsEvaluationExpression() {
    return context.watchableAsEvaluationExpression();
  }

  @NotNull
  @Override
  public DebuggerViewSupport getDebugProcess() {
    return context.getDebugProcess();
  }

  @Nullable
  @Override
  public VariableContext getParent() {
    return context;
  }

  @NotNull
  @Override
  public MemberFilter getMemberFilter() {
    return context.getMemberFilter();
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    value = variable.getValue();
    if (value == null) {
      ObsolescentAsyncResults.consume(((ObjectProperty)variable).evaluateGet(context.getEvaluateContext()), node, new PairConsumer<Value, XValueNode>() {
        @Override
        public void consume(Value value, XValueNode node) {
          VariableView.this.value = value;
          computePresentation(value, node);
        }
      });
    }
    else {
      computePresentation(value, node);
    }
  }

  @NotNull
  static String trimFunctionDescription(@NotNull Value value) {
    String presentableValue = value.getValueString();
    if (presentableValue == null) {
      return "";
    }

    int endIndex = 0;
    while (endIndex < presentableValue.length() && !StringUtil.isLineBreak(presentableValue.charAt(endIndex))) {
      endIndex++;
    }
    while (endIndex > 0 && Character.isWhitespace(presentableValue.charAt(endIndex - 1))) {
      endIndex--;
    }
    return presentableValue.substring(0, endIndex);
  }

  private void computePresentation(@NotNull Value value, @NotNull XValueNode node) {
    String valueString = value.getValueString();
    switch (value.getType()) {
      case OBJECT:
      case NODE:
        context.getDebugProcess().computeObjectPresentation(((ObjectValue)value), variable, context, node, getIcon());
        break;

      case FUNCTION:
        node.setPresentation(getIcon(), new ObjectValuePresentation(trimFunctionDescription(value)), true);
        break;

      case ARRAY:
        context.getDebugProcess().computeArrayPresentation(value, variable, context, node, getIcon());
        break;

      case BOOLEAN:
      case NULL:
      case UNDEFINED:
        node.setPresentation(getIcon(), new XKeywordValuePresentation(valueString), false);
        break;

      case NUMBER:
        node.setPresentation(getIcon(), createNumberPresentation(valueString), false);
        break;

      case STRING: {
        node.setPresentation(getIcon(), new XStringValuePresentation(valueString), false);
        // isTruncated in terms of debugger backend, not in our terms (i.e. sometimes we cannot control truncation),
        // so, even in case of StringValue, we check value string length
        if ((value instanceof StringValue && ((StringValue)value).isTruncated()) || valueString.length() > XValueNode.MAX_VALUE_LENGTH) {
          node.setFullValueEvaluator(new MyFullValueEvaluator(value));
        }
      }
      break;

      default:
        node.setPresentation(getIcon(), null, valueString, true);
    }
  }

  private static XValuePresentation createNumberPresentation(@NotNull String value) {
    return value.equals("NaN") || value.equals("Infinity") ? new XKeywordValuePresentation(value) : new XNumericValuePresentation(value);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    if (!(value instanceof ObjectValue) || ((ObjectValue)value).hasProperties() == ThreeState.NO) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }

    ObsolescentAsyncResults.consume(((ObjectValue)value).getProperties(), node, new PairConsumer<List<Variable>, XCompositeNode>() {
      @Override
      public void consume(List<Variable> variables, XCompositeNode node) {
        if (value instanceof ArrayValue) {
          // todo arrays could have not only indexes values
          return;
        }

        if (value.getType() == ValueType.ARRAY) {
          computeArrayRanges(variables, node);
          return;
        }

        int maxPropertiesToShow;
        if (value.getType() == ValueType.FUNCTION) {
          maxPropertiesToShow = Integer.MAX_VALUE;
        }
        else {
          maxPropertiesToShow = XCompositeNode.MAX_CHILDREN_TO_SHOW;
          List<Variable> list = remainingChildren;
          if (list != null && childrenModificationStamp == ((ObjectValue)value).getCacheStamp()) {
            int to = Math.min(remainingChildrenOffset + XCompositeNode.MAX_CHILDREN_TO_SHOW, list.size());
            boolean isLast = to == list.size();
            node.addChildren(Variables.createVariablesList(list, remainingChildrenOffset, to, VariableView.this), isLast);
            if (!isLast) {
              node.tooManyChildren(list.size() - to);
              remainingChildrenOffset += XCompositeNode.MAX_CHILDREN_TO_SHOW;
            }
            return;
          }
        }

        FunctionValue functionValue = value instanceof FunctionValue ? (FunctionValue)value : null;
        if (functionValue != null && functionValue.hasScopes() == ThreeState.NO) {
          functionValue = null;
        }

        remainingChildren = Variables.sortFilterAndAddValueList(variables, node, VariableView.this, maxPropertiesToShow, functionValue == null);
        if (remainingChildren != null) {
          remainingChildrenOffset = maxPropertiesToShow;
          childrenModificationStamp = ((ObjectValue)value).getCacheStamp();
        }

        if (functionValue != null) {
          // we pass context as variable context instead of this variable value - we cannot watch function scopes variables, so, this variable name doesn't matter
          node.addChildren(XValueChildrenList.bottomGroup(new FunctionScopesValueGroup(functionValue, context)), true);
        }
      }
    });

    if (value instanceof ArrayValue) {
      ObsolescentAsyncResults.consume(((ArrayValue)value).getVariables(), node, new PairConsumer<List<Variable>, XCompositeNode>() {
        @Override
        public void consume(List<Variable> variables, XCompositeNode node) {
          computeIndexedValuesRanges(variables, node);
        }
      });
    }
  }

  private void computeIndexedValuesRanges(@NotNull List<Variable> variables, @NotNull XCompositeNode node) {
    int totalLength = variables.size();
    int bucketSize = XCompositeNode.MAX_CHILDREN_TO_SHOW;
    if (totalLength <= bucketSize) {
      node.addChildren(Variables.createVariablesList(variables, this), true);
      return;
    }

    XValueChildrenList groupList = new XValueChildrenList();
    addGroups(variables, groupList, 0, totalLength, bucketSize);
    node.addChildren(groupList, true);
  }

  private void computeArrayRanges(@NotNull List<? extends Variable> properties, @NotNull XCompositeNode node) {
    List<Variable> variables = Variables.filterAndSort(properties, this, false);
    int count = variables.size();
    int bucketSize = XCompositeNode.MAX_CHILDREN_TO_SHOW;
    if (count <= bucketSize) {
      node.addChildren(Variables.createVariablesList(variables, this), true);
      return;
    }

    for (; count > 0; count--) {
      if (Character.isDigit(variables.get(count - 1).getName().charAt(0))) {
        break;
      }
    }

    XValueChildrenList groupList = new XValueChildrenList();
    if (count > 0) {
      addGroups(variables, groupList, 0, count, bucketSize);
    }

    int notGroupedVariablesOffset;
    if ((variables.size() - count) > bucketSize) {
      for (notGroupedVariablesOffset = variables.size(); notGroupedVariablesOffset > 0; notGroupedVariablesOffset--) {
        if (!variables.get(notGroupedVariablesOffset - 1).getName().startsWith(Variables.SPECIAL_PROPERTY_PREFIX)) {
          break;
        }
      }

      if (notGroupedVariablesOffset > 0) {
        addGroups(variables, groupList, count, notGroupedVariablesOffset, bucketSize);
      }
    }
    else {
      notGroupedVariablesOffset = count;
    }

    for (int i = notGroupedVariablesOffset; i < variables.size(); i++) {
      groupList.add(new VariableView(this, variables.get(i)));
    }

    node.addChildren(groupList, true);
  }

  private void addGroups(List<Variable> variables, XValueChildrenList groupList, int from, int limit, int bucketSize) {
    int to = Math.min(bucketSize, limit);
    boolean done = false;
    do {
      int groupFrom = from;
      int groupTo = to;

      from += bucketSize;
      to = from + Math.min(bucketSize, limit - from);

      // don't create group for only one member
      if (to - from == 1) {
        groupTo++;
        done = true;
      }
      groupList.addTopGroup(VariablesGroup.createArrayRangeGroup(groupFrom, groupTo, variables, this));
      if (from >= limit) {
        break;
      }
    }
    while (!done);
  }

  @NotNull
  private Icon getIcon() {
    return getIcon(value);
  }

  @Override
  @Nullable
  public XValueModifier getModifier() {
    if (!variable.isMutable()) {
      return null;
    }

    return new XValueModifier() {
      @Override
      public String getInitialValueEditorText() {
        if (value.getType() == ValueType.STRING) {
          String string = value.getValueString();
          StringBuilder builder = new StringBuilder(string.length());
          builder.append('"');
          StringUtil.escapeStringCharacters(string.length(), string, builder);
          builder.append('"');
          return builder.toString();
        }
        else {
          return value.getType().isObjectType() ? null : value.getValueString();
        }
      }

      @Override
      public void setValue(@NotNull String expression, @NotNull final XModificationCallback callback) {
        ValueModifier valueModifier = variable.getValueModifier();
        assert valueModifier != null;
        valueModifier.setValue(variable, expression, getEvaluateContext()).doWhenDone(new Runnable() {
          @Override
          public void run() {
            value = null;
            callback.valueModified();
          }
        }).doWhenRejected(createErrorMessageConsumer(callback));
      }
    };
  }

  private static Consumer<String> createErrorMessageConsumer(@NotNull final XValueCallback callback) {
    return new Consumer<String>() {
      @Override
      public void consume(@Nullable String errorMessage) {
        callback.errorOccurred(errorMessage == null ? "Internal error" : errorMessage);
      }
    };
  }

  @NotNull
  @Override
  public EvaluateContext getEvaluateContext() {
    return context.getEvaluateContext();
  }

  @Nullable
  public Value getValue() {
    return variable.getValue();
  }

  @Override
  public boolean canNavigateToSource() {
    return value instanceof FunctionValue || getDebugProcess().canNavigateToSource(variable, context);
  }

  @Override
  public void computeSourcePosition(@NotNull final XNavigatable navigatable) {
    if (value instanceof FunctionValue) {
      ((FunctionValue)value).resolve().doWhenDone(new Consumer<FunctionValue>() {
        @Override
        public void consume(final FunctionValue function) {
          getDebugProcess().getVm().getScriptManager().getScript(function).doWhenDone(new Consumer<Script>() {
            @Override
            public void consume(Script script) {
              navigatable.setSourcePosition(script == null ? null : getDebugProcess().getSourceInfo(null, script, function.getOpenParenLine(), function.getOpenParenColumn()));
            }
          });
        }
      });
    }
    else {
      getDebugProcess().computeSourcePosition(variable, context, navigatable);
    }
  }

  @Override
  @Nullable
  public String getEvaluationExpression() {
    if (!watchableAsEvaluationExpression()) {
      return null;
    }

    SmartList<String> list = new SmartList<String>(variable.getName());
    VariableContext parent = context;
    while (parent != null && parent.getName() != null) {
      list.add(parent.getName());
      parent = parent.getParent();
    }
    return context.getDebugProcess().propertyNamesToString(list, false);
  }

  private static class MyFullValueEvaluator extends XFullValueEvaluator {
    private final Value value;

    public MyFullValueEvaluator(@NotNull Value value) {
      super(value instanceof StringValue ? ((StringValue)value).getLength() : value.getValueString().length());

      this.value = value;
    }

    @Override
    public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
      if (!(value instanceof StringValue) || !((StringValue)value).isTruncated()) {
        callback.evaluated(value.getValueString());
        return;
      }

      final AtomicBoolean evaluated = new AtomicBoolean();
      ((StringValue)value).reloadHeavyValue().doWhenDone(new Runnable() {
        @Override
        public void run() {
          if (!callback.isObsolete() && evaluated.compareAndSet(false, true)) {
            callback.evaluated(value.getValueString());
          }
        }
      }).doWhenRejected(createErrorMessageConsumer(callback));
    }
  }

  @Nullable
  @Override
  public Scope getScope() {
    return context.getScope();
  }
}
