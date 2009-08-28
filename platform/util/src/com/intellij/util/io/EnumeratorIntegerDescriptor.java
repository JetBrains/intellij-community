/*
 * @author max
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EnumeratorIntegerDescriptor implements KeyDescriptor<Integer> {
  public int getHashCode(final Integer value) {
    return value.intValue();
  }

  public boolean isEqual(final Integer val1, final Integer val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput out, final Integer value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.intValue());
  }

  public Integer read(final DataInput in) throws IOException {
    return DataInputOutputUtil.readINT(in);
  }
}