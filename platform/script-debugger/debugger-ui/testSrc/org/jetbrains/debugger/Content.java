package org.jetbrains.debugger;

import com.intellij.util.SmartList;

import java.util.List;

public final class Content {
  public final List<TestCompositeNode> topGroups = new SmartList<>();
  public final List<TestValueNode> values = new SmartList<>();
  public final List<TestCompositeNode> bottomGroups = new SmartList<>();
}