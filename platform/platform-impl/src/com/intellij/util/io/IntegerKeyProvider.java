package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
   * @deprecated use {@link ByteBufferIntObjectMap} instead
 */
public class IntegerKeyProvider implements ByteBufferMap.KeyProvider<Integer> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.StringKeyProvider");

  public static final IntegerKeyProvider INSTANCE = new IntegerKeyProvider();

  private IntegerKeyProvider() {
  }

  public int hashCode(Integer key) {
    return key.hashCode();
  }

  public void write(DataOutput out, Integer key) throws IOException {
    out.writeInt(key.intValue());
  }

  public int length(Integer key) {
    return 4;
  }

  public Integer get(DataInput in) throws IOException {
    return new Integer(in.readInt());
  }

  public boolean equals(DataInput in, Integer key) throws IOException {
    return key.intValue() == in.readInt();
  }
}
