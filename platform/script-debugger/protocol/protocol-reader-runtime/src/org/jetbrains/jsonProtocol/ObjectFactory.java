package org.jetbrains.jsonProtocol;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.io.JsonReaderEx;

@ApiStatus.Internal
public abstract class ObjectFactory<T> {
  public abstract T read(JsonReaderEx reader);
}
