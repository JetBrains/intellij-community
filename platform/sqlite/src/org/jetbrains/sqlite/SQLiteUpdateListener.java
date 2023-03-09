// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite;

/** <a href="https://www.sqlite.org/c3ref/update_hook.html">...</a> */
public interface SQLiteUpdateListener {
  void onUpdate(Type type, String database, String table, long rowId);

  enum Type {
    INSERT,
    DELETE,
    UPDATE
  }
}
