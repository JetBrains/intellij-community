// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite;

import org.jetbrains.sqlite.core.SqliteConnection;

import java.sql.SQLException;

/** <a href="https://sqlite.org/c3ref/progress_handler.html">...</a> */
public abstract class ProgressHandler {
  protected abstract int progress();

  /**
   * Sets a progress handler for the connection.
   *
   * @param conn            the SQLite connection
   * @param vmCalls         the approximate number of virtual machine instructions that are evaluated
   *                        between successive invocations of the progressHandler
   * @param progressHandler the progressHandler
   */
  public static void setHandler(
    SqliteConnection conn, int vmCalls, ProgressHandler progressHandler) throws SQLException {
    if (conn.isClosed()) {
      throw new SQLException("connection closed");
    }
    conn.db.register_progress_handler(vmCalls, progressHandler);
  }

  /**
   * Clears any progress handler registered with the connection.
   *
   * @param conn the SQLite connection
   */
  public static void clearHandler(SqliteConnection conn) throws SQLException {
    conn.db.clear_progress_handler();
  }
}
