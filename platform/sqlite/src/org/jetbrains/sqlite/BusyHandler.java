// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite;

import kotlin.Unit;

import java.io.IOException;

/** <a href="https://www.sqlite.org/c3ref/busy_handler.html">...</a> */
public abstract class BusyHandler {
  /**
   * <a href="https://www.sqlite.org/c3ref/busy_handler.html">...</a>
   *
   * @param nbPrevInvok number of times that the busy handler has been invoked previously for the
   *                    same locking event
   * @return If the busy callback returns 0, then no additional attempts are made to access the
   * database and SQLITE_BUSY is returned to the application. If the callback returns
   * non-zero, then another attempt is made to access the database and the cycle repeats.
   */
  protected abstract int callback(int nbPrevInvok);

  /**
   * commit the busy handler for the connection.
   *
   * @param connection        the SQLite connection
   * @param busyHandler the busyHandler
   */
  private static void commitHandler(SqliteConnection connection, BusyHandler busyHandler) throws IOException {
    if (connection.isClosed()) {
      throw new IOException("connection closed");
    }

    connection.useDb$intellij_platform_sqlite(db -> {
      db.busy_handler(busyHandler);
      return Unit.INSTANCE;
    });
  }

  /**
   * Sets a busy handler for the connection.
   *
   * @param conn        the SQLite connection
   * @param busyHandler the busyHandler
   */
  public static void setHandler(SqliteConnection conn, BusyHandler busyHandler)
    throws IOException {
    commitHandler(conn, busyHandler);
  }

  /**
   * Clears any busy handler registered with the connection.
   *
   * @param conn the SQLite connection
   */
  public static void clearHandler(SqliteConnection conn) throws IOException {
    commitHandler(conn, null);
  }
}
