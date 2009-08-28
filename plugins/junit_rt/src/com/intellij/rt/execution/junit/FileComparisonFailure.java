package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.ComparisonFailure;

public class FileComparisonFailure extends ComparisonFailure implements KnownException {
  private final String myExpected;
  private final String myActual;
  private final String myFilePath;

  public FileComparisonFailure(String message, String expected, String actual, String filePath) {
    super(message, expected, actual);
    myExpected = expected;
    myActual = actual;
    myFilePath = filePath;
  }

  public PacketFactory getPacketFactory() {
    return new MyPacketFactory(this, myExpected, myActual, myFilePath);
  }

  private static class MyPacketFactory extends ComparisonDetailsExtractor {
    private final String myFilePath;

    public MyPacketFactory(ComparisonFailure assertion, String expected, String actual, String filePath) {
      super(assertion, expected, actual);
      myFilePath = filePath;
    }

    public Packet createPacket(OutputObjectRegistryEx registry, Object test) {
      Packet packet = super.createPacket(registry, test);
      packet.addLimitedString(myFilePath);
      return packet;
    }
  }
}
