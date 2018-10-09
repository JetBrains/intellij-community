package org.jetbrains.jsonProtocol;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.JsonReaderEx;

import java.util.Map;

final class MapFactory<T> extends ObjectFactory<Map<String, T>> {
  private final ObjectFactory<? extends T> valueFactory;

  MapFactory(@NotNull ObjectFactory<? extends T> valueFactory) {
    this.valueFactory = valueFactory;
  }

  @Override
  public Map<String, T> read(JsonReaderEx reader) {
    return JsonReaders.readMap(reader, valueFactory);
  }
}
