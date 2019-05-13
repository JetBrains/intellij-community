package org.jetbrains.jsonProtocol;

import org.jetbrains.io.JsonReaderEx;

public abstract class ObjectFactory<T> {
  public abstract T read(JsonReaderEx reader);
}
