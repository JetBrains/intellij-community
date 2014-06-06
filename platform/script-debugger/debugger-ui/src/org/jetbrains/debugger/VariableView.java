package org.jetbrains.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.ObsolescentAsyncResults;
import com.intellij.xdebugger.XSourcePositionWrapper;
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

  public VariableView(@NotNull Variable variable, @NotNull VariableContext context) {
    super(context.getViewSupport().normalizeMemberName(variable));

    this.context = context;
    this.variable = variable;
  }

  @NotNull
  public static String getClassName(@NotNull ObjectValue value) {
    String className = value.getClassName();
    return StringUtil.isEmpty(className) ? "Object" : className;
  }

  @NotNull
  public static String getObjectValueDescription(@NotNull ObjectValue value) {
    String description = value.getValueString();
    return StringUtil.isEmpty(description) ? getClassName(value) : description;
  }

  public static void setObjectPresentation(@NotNull ObjectValue value, @NotNull Icon icon, @NotNull XValueNode node) {
    node.setPresentation(icon, new ObjectValuePresentation(getObjectValueDescription(value)), value.hasProperties() != ThreeState.NO);
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
  public DebuggerViewSupport getViewSupport() {
    return context.getViewSupport();
  }

  @Nullable
  @Override
  public VariableContext getParent() {
    return context;
  }

  @NotNull
  @Override
  public MemberFilter createMemberFilter() {
    return context.getViewSupport().createMemberFilter(this);
  }

  @Override
  public void computePresentation(@NotNull final XValueNode node, @NotNull XValuePlace place) {
    value = variable.getValue();
    if (value != null) {
      computePresentation(value, node);
      return;
    }

    if (!(variable instanceof ObjectProperty) || ((ObjectProperty)variable).getGetter() == null) {
      // it is "used" expression (WEB-6779 Debugger/Variables: Automatically show used variables)
      ObsolescentAsyncResults.consume(getEvaluateContext().evaluate(variable.getName()), node, new PairConsumer<Value, XValueNode>() {
        @Override
        public void consume(Value value, XValueNode node) {
          VariableView.this.value = value;
          computePresentation(value, node);
        }
      });
      return;
    }

    node.setPresentation(null, new XValuePresentation() {
      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
        renderer.renderValue("\u2026");
      }
    }, false);
    node.setFullValueEvaluator(new XFullValueEvaluator(" (invoke getter)") {
      @Override
      public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
        ValueModifier valueModifier = variable.getValueModifier();
        assert valueModifier != null;
        ObsolescentAsyncResults.consume(valueModifier.evaluateGet(variable, getEvaluateContext()), node, new PairConsumer<Value, XValueNode>() {
          @Override
          public void consume(Value value, XValueNode node) {
            callback.evaluated("");
            VariableView.this.value = value;
            computePresentation(value, node);
          }
        });
      }
    }.setShowValuePopup(false));
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
        context.getViewSupport().computeObjectPresentation(((ObjectValue)value), variable, context, node, getIcon());
        break;

      case FUNCTION:
        node.setPresentation(getIcon(), new ObjectValuePresentation(trimFunctionDescription(value)), true);
        break;

      case ARRAY:
        context.getViewSupport().computeArrayPresentation(value, variable, context, node, getIcon());
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
  public void computeChildren(@NotNull final XCompositeNode node) {
    node.setAlreadySorted(true);

    if (!(value instanceof ObjectValue)) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }

    List<Variable> list = remainingChildren;
    if (list != null) {
      int to = Math.min(remainingChildrenOffset + XCompositeNode.MAX_CHILDREN_TO_SHOW, list.size());
      boolean isLast = to == list.size();
      node.addChildren(Variables.createVariablesList(list, remainingChildrenOffset, to, this), isLast);
      if (!isLast) {
        node.tooManyChildren(list.size() - to);
        remainingChildrenOffset += XCompositeNode.MAX_CHILDREN_TO_SHOW;
      }
      return;
    }

    final ObjectValue objectValue = (ObjectValue)value;

    final boolean hasNamedProperties = objectValue.hasProperties() != ThreeState.NO;
    boolean hasIndexedProperties = objectValue.hasIndexedProperties() != ThreeState.NO;
    ActionCallback.Chunk chunk = new ActionCallback.Chunk();

    ActionCallback additionalProperties = getViewSupport().computeAdditionalObjectProperties(objectValue, variable, this, node);
    if (additionalProperties != null) {
      chunk.add(additionalProperties);
    }

    // we don't support indexed properties if additional properties added - behavior is undefined if object has indexed properties and additional properties also specified
    if (hasIndexedProperties) {
      chunk.add(computeIndexedProperties((ArrayValue)objectValue, node, !hasNamedProperties && additionalProperties == null));
    }

    if (hasNamedProperties) {
      // named properties should be added after additional properties
      final ActionCallback namedPropertiesCallback;
      if (additionalProperties == null || additionalProperties.isProcessed()) {
        namedPropertiesCallback = computeNamedProperties(objectValue, node, !hasIndexedProperties && additionalProperties == null);
      }
      else {
        namedPropertiesCallback = new ActionCallback();
        additionalProperties.doWhenDone(new Runnable() {
          @Override
          public void run() {
            computeNamedProperties(objectValue, node, true).notify(namedPropertiesCallback);
          }
        }).notifyWhenRejected(namedPropertiesCallback);
      }
      chunk.add(namedPropertiesCallback);
    }

    if (hasIndexedProperties == hasNamedProperties || additionalProperties != null) {
      chunk.create().doWhenProcessed(new Runnable() {
        @Override
        public void run() {
          if (!node.isObsolete()) {
            node.addChildren(XValueChildrenList.EMPTY, true);
          }
        }
      });
    }
  }

  public abstract static class ObsolescentIndexedVariablesConsumer extends IndexedVariablesConsumer {
    protected final XCompositeNode node;

    protected ObsolescentIndexedVariablesConsumer(@NotNull XCompositeNode node) {
      this.node = node;
    }

    @Override
    public boolean isObsolete() {
      return node.isObsolete();
    }
  }

  @NotNull
  private ActionCallback computeIndexedProperties(@NotNull final ArrayValue value, @NotNull final XCompositeNode node, final boolean isLastChildren) {
    return value.getIndexedProperties(0, value.getLength(), XCompositeNode.MAX_CHILDREN_TO_SHOW, new ObsolescentIndexedVariablesConsumer(node) {
      @Override
      public void consumeRanges(@Nullable int[] ranges) {
        if (ranges == null) {
          XValueChildrenList groupList = new XValueChildrenList();
          LazyVariablesGroup.addGroups(value, LazyVariablesGroup.GROUP_FACTORY, groupList, 0, value.getLength(), XCompositeNode.MAX_CHILDREN_TO_SHOW, VariableView.this);
          node.addChildren(groupList, isLastChildren);
        }
        else {
          LazyVariablesGroup.addRanges(value, ranges, node, VariableView.this, isLastChildren);
        }
      }

      @Override
      public void consumeVariables(@NotNull List<Variable> variables) {
        node.addChildren(Variables.createVariablesList(variables, VariableView.this), isLastChildren);
      }
    }, null);
  }

  private ActionCallback computeNamedProperties(@NotNull final ObjectValue value, @NotNull XCompositeNode node, final boolean isLastChildren) {
    return ObsolescentAsyncResults.consume(value.getProperties(), node, new PairConsumer<List<Variable>, XCompositeNode>() {
      @Override
      public void consume(List<Variable> variables, XCompositeNode node) {
        if (value.getType() == ValueType.ARRAY && !(value instanceof ArrayValue)) {
          computeArrayRanges(variables, node);
          return;
        }

        FunctionValue functionValue = value instanceof FunctionValue ? (FunctionValue)value : null;
        if (functionValue != null && functionValue.hasScopes() == ThreeState.NO) {
          functionValue = null;
        }

        remainingChildren = Variables.sortFilterAndAddValueList(variables, node, VariableView.this, XCompositeNode.MAX_CHILDREN_TO_SHOW, isLastChildren && functionValue == null);
        if (remainingChildren != null) {
          remainingChildrenOffset = XCompositeNode.MAX_CHILDREN_TO_SHOW;
        }

        if (functionValue != null) {
          // we pass context as variable context instead of this variable value - we cannot watch function scopes variables, so, this variable name doesn't matter
          node.addChildren(XValueChildrenList.bottomGroup(new FunctionScopesValueGroup(functionValue, context)), isLastChildren);
        }
      }
    });
  }

  private void computeArrayRanges(@NotNull List<? extends Variable> properties, @NotNull XCompositeNode node) {
    final List<Variable> variables = Variables.filterAndSort(properties, this, false);
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
      LazyVariablesGroup.addGroups(variables, VariablesGroup.GROUP_FACTORY, groupList, 0, count, bucketSize, this);
    }

    int notGroupedVariablesOffset;
    if ((variables.size() - count) > bucketSize) {
      for (notGroupedVariablesOffset = variables.size(); notGroupedVariablesOffset > 0; notGroupedVariablesOffset--) {
        if (!variables.get(notGroupedVariablesOffset - 1).getName().startsWith(Variables.SPECIAL_PROPERTY_PREFIX)) {
          break;
        }
      }

      if (notGroupedVariablesOffset > 0) {
        LazyVariablesGroup.addGroups(variables, VariablesGroup.GROUP_FACTORY, groupList, count, notGroupedVariablesOffset, bucketSize, this);
      }
    }
    else {
      notGroupedVariablesOffset = count;
    }

    for (int i = notGroupedVariablesOffset; i < variables.size(); i++) {
      groupList.add(new VariableView(variables.get(i), this));
    }

    node.addChildren(groupList, true);
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
    return value instanceof FunctionValue || getViewSupport().canNavigateToSource(variable, context);
  }

  @Override
  public void computeSourcePosition(@NotNull final XNavigatable navigatable) {
    if (value instanceof FunctionValue) {
      ((FunctionValue)value).resolve().doWhenDone(new Consumer<FunctionValue>() {
        @Override
        public void consume(final FunctionValue function) {
          getViewSupport().getVm().getScriptManager().getScript(function).doWhenDone(new Consumer<Script>() {
            @Override
            public void consume(Script script) {
              SourceInfo position = script == null ? null : getViewSupport().getSourceInfo(null, script, function.getOpenParenLine(), function.getOpenParenColumn());
              navigatable.setSourcePosition(position == null ? null : new XSourcePositionWrapper(position) {
                @NotNull
                @Override
                public Navigatable createNavigatable(@NotNull Project project) {
                  Navigatable result = PsiVisitors.visit(myPosition, project, new PsiVisitors.Visitor<Navigatable>() {
                    @Override
                    public Navigatable visit(@NotNull PsiElement element, int positionOffset, @NotNull Document document) {
                      // element will be "open paren", but we should navigate to function name,
                      // we cannot use specific PSI type here (like JSFunction), so, we try to find reference expression (i.e. name expression)
                      PsiElement referenceCandidate = element;
                      PsiElement psiReference = null;
                      while ((referenceCandidate = referenceCandidate.getPrevSibling()) != null) {
                        if (referenceCandidate instanceof PsiReference) {
                          psiReference = referenceCandidate;
                          break;
                        }
                      }

                      if (psiReference == null) {
                        referenceCandidate = element.getParent();
                        if (referenceCandidate != null) {
                          while ((referenceCandidate = referenceCandidate.getPrevSibling()) != null) {
                            if (referenceCandidate instanceof PsiReference) {
                              psiReference = referenceCandidate;
                              break;
                            }
                          }
                        }
                      }

                      PsiElement navigationElement = psiReference == null ? element.getNavigationElement() : psiReference.getNavigationElement();
                      return navigationElement instanceof Navigatable ? (Navigatable)navigationElement : null;
                    }
                  }, null);
                  return result == null ? super.createNavigatable(project) : result;
                }
              });
            }
          });
        }
      });
    }
    else {
      getViewSupport().computeSourcePosition(variable, context, navigatable);
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
    return context.getViewSupport().propertyNamesToString(list, false);
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
      ((StringValue)value).getFullString().doWhenDone(new Runnable() {
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
