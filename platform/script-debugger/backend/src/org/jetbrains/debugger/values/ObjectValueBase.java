package org.jetbrains.debugger.values;

import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.ObsolescentAsyncFunction;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.EvaluateContext;
import org.jetbrains.debugger.ValueModifier;
import org.jetbrains.debugger.Variable;
import org.jetbrains.debugger.VariablesHost;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  protected VariablesHost<VALUE_LOADER> childrenManager;

  public ObjectValueBase(@NotNull ValueType type) {
    super(type);
  }

  @NotNull
  @Override
  public final Promise<List<Variable>> getProperties() {
    return childrenManager.get();
  }

  abstract class MyObsolescentAsyncFunction<PARAM, RESULT> implements ObsolescentAsyncFunction<PARAM, RESULT> {
    private final Obsolescent obsolescent;

    MyObsolescentAsyncFunction(@NotNull Obsolescent obsolescent) {
      this.obsolescent = obsolescent;
    }

    @Override
    public boolean isObsolete() {
      return obsolescent.isObsolete() || childrenManager.valueManager.isObsolete();
    }
  }

  @NotNull
  @Override
  public Promise<List<Variable>> getProperties(@NotNull final List<String> names, @NotNull final EvaluateContext evaluateContext, @NotNull final Obsolescent obsolescent) {
    return getProperties()
      .then(new MyObsolescentAsyncFunction<List<Variable>, List<Variable>>(obsolescent) {
        @NotNull
        @Override
        public Promise<List<Variable>> fun(List<Variable> variables) {
          return getSpecifiedProperties(variables, names, evaluateContext);
        }
      });
  }

  @NotNull
  protected static Promise<List<Variable>> getSpecifiedProperties(@NotNull List<Variable> variables, @NotNull final List<String> names, @NotNull EvaluateContext evaluateContext) {
    final List<Variable> properties = new SmartList<Variable>();
    int getterCount = 0;
    for (Variable property : variables) {
      if (!property.isReadable() || !names.contains(property.getName())) {
        continue;
      }

      if (!properties.isEmpty()) {
        Collections.sort(properties, new Comparator<Variable>() {
          @Override
          public int compare(@NotNull Variable o1, @NotNull Variable o2) {
            return names.indexOf(o1.getName()) - names.indexOf(o2.getName());
          }
        });
      }

      properties.add(property);
      if (property.getValue() == null) {
        getterCount++;
      }
    }

    if (getterCount == 0) {
      return Promise.resolve(properties);
    }
    else {
      List<Promise<?>> promises = new SmartList<Promise<?>>();
      for (Variable variable : properties) {
        if (variable.getValue() == null) {
          ValueModifier valueModifier = variable.getValueModifier();
          assert valueModifier != null;
          promises.add(valueModifier.evaluateGet(variable, evaluateContext));
        }
      }
      return Promise.all(promises, properties);
    }
  }

  @Nullable
  @Override
  public String getValueString() {
    return null;
  }

  @NotNull
  @Override
  public ThreeState hasProperties() {
    return ThreeState.UNSURE;
  }

  @NotNull
  @Override
  public ThreeState hasIndexedProperties() {
    return ThreeState.NO;
  }

  @NotNull
  @Override
  public Promise<Void> getIndexedProperties(int from, int to, int bucketThreshold, @NotNull IndexedVariablesConsumer consumer, @Nullable ValueType componentType) {
    return Promise.REJECTED;
  }

  @NotNull
  @Override
  public VariablesHost getVariablesHost() {
    return childrenManager;
  }
}