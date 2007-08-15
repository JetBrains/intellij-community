package com.intellij.execution.junit2.segments;

public interface PacketReader {
  void readPacketFrom(ObjectReader reader);
  void onFinished();
}
