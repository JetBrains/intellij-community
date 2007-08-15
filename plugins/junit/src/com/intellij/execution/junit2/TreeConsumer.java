package com.intellij.execution.junit2;

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.junit2.segments.PacketConsumer;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;

public abstract class TreeConsumer implements PacketConsumer {
  public void readPacketFrom(final ObjectReader reader) {
    final TestProxy root = readNode(reader);
    onTreeAvailable(root);
  }

  public String getPrefix() {
    return PoolOfDelimiters.TREE_PREFIX;
  }

  public void onFinished() {
  }

  protected abstract void onTreeAvailable(TestProxy treeRoot);

  private static TestProxy readNode(final ObjectReader reader) {
    final TestProxy node = reader.readObject();
    final int childCount = reader.readInt();
    for (int i = 0; i < childCount; i++)
      node.addChild(readNode(reader));
    return node;
  }
}
