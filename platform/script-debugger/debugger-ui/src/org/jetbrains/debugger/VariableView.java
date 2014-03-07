package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XKeywordValuePresentation;
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation;
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class VariableView extends VariableViewBase implements VariableContext {
  private static final Pattern ARRAY_DESCRIPTION_PATTERN = Pattern.compile("^[a-zA-Z]+\\[\\d+\\]$");

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
    return StringUtil.notNullize(value.getClassName(), "Object");
  }

  @Override
  public boolean watchableAsEvaluationExpression() {
    return true;
  }

  @NotNull
  @Override
  public DebugProcessEx getDebugProcess() {
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

  static String trimFunctionDescription(@NotNull Value value) {
    String presentableValue = value.getValueString();
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
        computeObjectPresentation(value, node);
        break;

      case FUNCTION: {
        node.setPresentation(getIcon(), new ObjectValuePresentation(trimFunctionDescription(value)), true);
      }
      break;

      case ARRAY: {
        // only WIP reports normal description
        if (valueString.endsWith("]") && ARRAY_DESCRIPTION_PATTERN.matcher(valueString).find()) {
          node.setPresentation(getIcon(), null, valueString, true);
        }
        else {
          ObsolescentAsyncResults.consume(context.getEvaluateContext().evaluate("a.length", Collections.<String, EvaluateContextAdditionalParameter>singletonMap("a", value)), node,
                                          new PairConsumer<Value, XValueNode>() {
                                            @Override
                                            public void consume(Value lengthValue, XValueNode node) {
                                              node.setPresentation(getIcon(), null, "Array[" + lengthValue.getValueString() + ']', true);
                                            }
                                          });
        }
      }
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
        if (value.isTruncated() || valueString.length() > XValueNode.MAX_VALUE_LENGTH) {
          node.setFullValueEvaluator(new MyFullValueEvaluator(value.getActualLength()));
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

  private void computeObjectPresentation(@NotNull Value value, @NotNull XValueNode node) {
    ObjectValue objectValue = value.asObject();
    assert objectValue != null;
    context.getDebugProcess().computeObjectPresentation(objectValue, variable, context, node, getIcon());
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setAlreadySorted(true);

    ObjectValue object = value.asObject();
    if (object == null) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }

    ObsolescentAsyncResults.consume(object.getProperties(), node, new PairConsumer<ObjectPropertyData, XCompositeNode>() {
      @Override
      public void consume(ObjectPropertyData data, XCompositeNode node) {
        if (value.getType() == ValueType.ARRAY) {
          computeArrayRanges(data.getProperties(), node);
          return;
        }

        int maxPropertiesToShow;
        if (value.getType() == ValueType.FUNCTION) {
          maxPropertiesToShow = Integer.MAX_VALUE;
        }
        else {
          maxPropertiesToShow = XCompositeNode.MAX_CHILDREN_TO_SHOW;
          List<Variable> list = remainingChildren;
          if (list != null && childrenModificationStamp == data.getCacheState()) {
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

        remainingChildren = Variables.sortFilterAndAddValueList(data.getProperties(), node, VariableView.this, maxPropertiesToShow, value.getType() != ValueType.FUNCTION);
        if (remainingChildren != null) {
          remainingChildrenOffset = maxPropertiesToShow;
          childrenModificationStamp = data.getCacheState();
        }

        if (value.getType() == ValueType.FUNCTION) {
          // we pass context as variable context instead of this variable value - we cannot watch function scopes variables, so, this variable name doesn't matter
          node.addChildren(XValueChildrenList.bottomGroup(new FunctionScopesValueGroup(value.asObject(), context)), true);
        }
      }
    });
  }

  private void computeArrayRanges(List<? extends ObjectProperty> properties, XCompositeNode node) {
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

  @Override
  public ValueType getValueType() {
    return value.getType();
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

  public Value getValue() {
    return variable.getValue();
  }

  @Override
  public boolean canNavigateToSource() {
    // todo WEB-4369
    return value.getType() == ValueType.FUNCTION;
  }

  @Override
  public void computeSourcePosition(@NotNull final XNavigatable navigatable) {
    ObjectValue object = value.asObject();
    AsyncResult<FunctionValue> asFunction = object != null ? object.asFunction() : null;
    if (asFunction != null) {
      asFunction.doWhenDone(new Consumer<FunctionValue>() {
        @Override
        public void consume(FunctionValue function) {
          DebugProcessEx debugProcess = getDebugProcess();
          Script script = debugProcess.getVm().getScriptManager().getScript(function);
          navigatable.setSourcePosition(script == null ? null : debugProcess.getSourceInfo(null, script, function.getOpenParenLine(), function.getOpenParenColumn()));
        }
      });
    }
  }

  @Override
  @Nullable
  public String getEvaluationExpression() {
    if (!context.watchableAsEvaluationExpression()) {
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

  private class MyFullValueEvaluator extends XFullValueEvaluator {
    public MyFullValueEvaluator(int actualLength) {
      super(actualLength);
    }

    @Override
    public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
      if (!value.isTruncated()) {
        callback.evaluated(value.getValueString());
        return;
      }

      final AtomicBoolean evaluated = new AtomicBoolean();
      value.reloadHeavyValue().doWhenDone(new Runnable() {
        @Override
        public void run() {
          if (!callback.isObsolete() && evaluated.compareAndSet(false, true)) {
            callback.evaluated(value.getValueString());
          }
        }
      }).doWhenRejected(createErrorMessageConsumer(callback));
    }
  }
}
