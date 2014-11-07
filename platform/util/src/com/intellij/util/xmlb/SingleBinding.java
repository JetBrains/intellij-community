package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class SingleBinding extends Binding {
  protected SingleBinding(Accessor accessor) {
    super(accessor);
  }

  @Nullable
  @Override
  public Object deserializeList(Object context, @NotNull List<?> nodes) {
    assert nodes.size() == 1;
    return deserialize(context, nodes.get(0));
  }
}