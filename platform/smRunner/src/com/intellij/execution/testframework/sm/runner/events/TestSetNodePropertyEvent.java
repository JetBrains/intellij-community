package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TestSetNodePropertyEvent extends TreeNodeEvent {

  private static final String ATTR_PROPERTY_KEY = "key";
  private static final String ATTR_PROPERTY_VALUE = "value";

  private final NodePropertyKey myPropertyKey;
  private final @Nullable String myPropertyValue;

  public TestSetNodePropertyEvent(final @Nullable String id,
                                  final @NotNull TestSetNodePropertyEvent.NodePropertyKey propertyKey,
                                  final @Nullable String propertyValue) {
    super(null, id);
    myPropertyKey = propertyKey;
    myPropertyValue = propertyValue;
  }

  public @NotNull NodePropertyKey getPropertyKey() {
    return myPropertyKey;
  }

  public @Nullable String getPropertyValue() {
    return myPropertyValue;
  }

  @Override
  protected void appendToStringInfo(final @NotNull StringBuilder buf) {
    append(buf, "propertyKey", myPropertyKey);
    append(buf, "propertyValue", myPropertyValue);
  }

  public static @Nullable NodePropertyKey getPropertyKey(final @NotNull ServiceMessage msg) {
    return NodePropertyKey.findByName(msg.getAttributes().get(ATTR_PROPERTY_KEY));
  }

  public static @Nullable String getPropertyValue(final @NotNull ServiceMessage msg) {
    return msg.getAttributes().get(ATTR_PROPERTY_VALUE);
  }

  public enum NodePropertyKey {
    PRESENTABLE_NAME("name");

    final String myKeyName;

    NodePropertyKey(final @NotNull String keyName) {
      this.myKeyName = keyName;
    }

    static NodePropertyKey findByName(final @Nullable String name) {
      for (NodePropertyKey value : values()) {
        if (value.myKeyName.equals(name)) {
          return value;
        }
      }
      return null;
    }

    @Override
    public @NotNull String toString() {
      return myKeyName;
    }
  }
}
