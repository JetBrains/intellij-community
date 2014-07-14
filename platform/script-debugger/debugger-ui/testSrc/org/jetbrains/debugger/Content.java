package org.jetbrains.debugger;

import com.intellij.util.SmartList;

import java.util.List;

public final class Content {
  public final List<TestCompositeNode> topGroups = new SmartList<TestCompositeNode>();
  public final List<TestValueNode> values = new SmartList<TestValueNode>();
  public final List<TestCompositeNode> bottomGroups = new SmartList<TestCompositeNode>();
}