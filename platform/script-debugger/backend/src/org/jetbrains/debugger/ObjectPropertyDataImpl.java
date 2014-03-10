package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ObjectPropertyDataImpl implements ObjectPropertyData {
  private final int cacheState;

  protected final List<? extends Variable> properties;
  protected final List<Variable> internalProperties;

  public ObjectPropertyDataImpl(int cacheState, List<? extends Variable> properties) {
    this(cacheState, properties, Collections.<Variable>emptyList());
  }

  public ObjectPropertyDataImpl(int cacheState, List<? extends Variable> properties, List<Variable> internalProperties) {
    this.cacheState = cacheState;
    this.properties = properties;
    this.internalProperties = internalProperties;
  }

  @NotNull
  @Override
  public List<? extends Variable> getProperties() {
    return properties;
  }

  @NotNull
  @Override
  public List<? extends Variable> getInternalProperties() {
    return internalProperties;
  }

  @Override
  public final int getCacheState() {
    return cacheState;
  }

  @Nullable
  @Override
  public Variable getProperty(String name) {
    Variable result = findProperty(name, properties);
    return result == null ? findProperty(name, internalProperties) : result;
  }

  @Nullable
  private static Variable findProperty(String name, Collection<? extends Variable> properties) {
    for (Variable property : properties) {
      if (property.getName().equals(name)) {
        return property;
      }
    }
    return null;
  }
}