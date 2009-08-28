package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface DataExternalizer<T> {
  void save(DataOutput out, T value) throws IOException;

  T read(DataInput in) throws IOException;
}
