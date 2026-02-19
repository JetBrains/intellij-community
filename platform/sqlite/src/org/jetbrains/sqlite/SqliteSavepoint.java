// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite;

public final class SqliteSavepoint {
  final int id;

  final String name;

  SqliteSavepoint(int id) {
    this.id = id;
    name = null;
  }

  SqliteSavepoint(int id, String name) {
    this.id = id;
    this.name = name;
  }

  public int getSavepointId() {
    return id;
  }

  public String getSavepointName() {
    return name == null ? String.format("SQLITE_SAVEPOINT_%s", id) : name;
  }
}
