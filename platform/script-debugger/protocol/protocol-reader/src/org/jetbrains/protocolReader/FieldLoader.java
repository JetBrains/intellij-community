package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class FieldLoader {
  final String name;
  final String jsonName;
  final ValueReader valueReader;
  final boolean skipRead;

  FieldLoader(@NotNull String name, @NotNull String jsonName, @NotNull ValueReader valueReader, boolean skipRead) {
    this.name = name;
    this.jsonName = jsonName;
    this.valueReader = valueReader;
    this.skipRead = skipRead;
  }
}
