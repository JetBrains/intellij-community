// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite.core;

import org.jetbrains.sqlite.*;

import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SqliteConnection {
  private final AtomicInteger savePoint = new AtomicInteger(0);
  private final DB db;
  private final SQLiteConnectionConfig connectionConfig;
  private Map<String, Class<?>> typeMap;
  private boolean readOnly = false;
  private SQLiteConfig.TransactionMode currentTransactionMode;
  private boolean firstStatementExecuted;

  public SqliteConnection(String url, String fileName, SQLiteConfig config) throws SQLException {
    DB newDB = null;
    try {
      db = newDB = open(url, fileName, config);
      connectionConfig = db.getConfig().newConnectionConfig();
      config.apply(this);
      currentTransactionMode = getDatabase().getConfig().getTransactionMode();
      // connection starts in "clean" state (even though some PRAGMA statements were executed)
      firstStatementExecuted = false;

      db.exec(this.transactionPrefix());
      currentTransactionMode = connectionConfig.getTransactionMode();
    }
    catch (Throwable t) {
      if (newDB != null) {
        try {
          newDB.close();
        }
        catch (Exception e) {
          t.addSuppressed(e);
        }
      }
      throw t;
    }
  }

  public SqliteStatement createStatement(int rst, int rsc, int rsh) throws SQLException {
    checkOpen();
    checkCursor(rst, rsc, rsh);

    return new SqliteStatement(this);
  }

  public SqlitePreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh) throws SQLException {
    checkOpen();
    checkCursor(rst, rsc, rsh);
    return new SqlitePreparedStatement(this, sql);
  }

  // JDBC 4

  /** @see Connection#isClosed() */
  public boolean isClosed() throws SQLException {
    return db.isClosed();
  }

  public boolean isValid(int timeout) throws SQLException {
    if (isClosed()) {
      return false;
    }
    try (SqliteStatement statement = createStatement()) {
      return statement.execute("select 1");
    }
  }

  /**
   * This will try to enforce the transaction mode if SQLiteConfig#isExplicitReadOnly is true and
   * auto commit is disabled.
   *
   * <ul>
   *   <li>If this connection is read only, the PRAGMA query_only will be set
   *   <li>If this connection is not read only:
   *       <ul>
   *         <li>if no statement has been executed, PRAGMA query_only will be set to false, and an
   *             IMMEDIATE transaction will be started
   *         <li>if a statement has already been executed, an exception is thrown
   *       </ul>
   * </ul>
   *
   * @throws SQLException if a statement has already been executed on this connection, then the
   *                      transaction cannot be upgraded to write
   */
  void tryEnforceTransactionMode() throws SQLException {
    // important note: read-only mode is only supported when auto-commit is disabled
    if (getDatabase().getConfig().isExplicitReadOnly() && getCurrentTransactionMode() != null) {
      if (isReadOnly()) {
        // this is a read-only transaction, make sure all writing operations are rejected by
        // the DB
        // (note: this pragma is evaluated on a per-transaction basis by SQLite)
        getDatabase()._exec("PRAGMA query_only = true;");
      }
      else if (getCurrentTransactionMode() == SQLiteConfig.TransactionMode.DEFERRED) {
        if (isFirstStatementExecuted()) {
          // first statement was already executed; cannot upgrade to write
          // transaction!
          throw new SQLException(
            "A statement has already been executed on this connection; cannot upgrade to write transaction");
        }
        else {
          // this is the first statement in the transaction; close and create an
          // immediate one
          getDatabase()._exec("commit; /* need to explicitly upgrade transaction */");

          // start the write transaction
          getDatabase()._exec("PRAGMA query_only = false;");
          getDatabase()
            ._exec("BEGIN IMMEDIATE; /* explicitly upgrade transaction */");
          setCurrentTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        }
      }
    }
  }

  /** @see Connection#isReadOnly() */
  public boolean isReadOnly() {
    SQLiteConfig config = getDatabase().getConfig();
    return (
      // the entire database is read-only
      ((config.getOpenModeFlags() & SQLiteOpenMode.READONLY.flag) != 0)
      // the flag was set explicitly by the user on this connection
      || (config.isExplicitReadOnly() && readOnly));
  }

  /** @see Connection#setReadOnly(boolean) */
  public void setReadOnly(boolean ro) throws SQLException {
    if (getDatabase().getConfig().isExplicitReadOnly()) {
      if (ro != readOnly && isFirstStatementExecuted()) {
        throw new SQLException(
          "Cannot change Read-Only status of this connection: the first statement was"
          + " already executed and the transaction is open.");
      }
    }
    else {
      // trying to change read-only flag
      if (ro != isReadOnly()) {
        throw new SQLException(
          "Cannot change read-only flag after establishing a connection."
          + " Use SQLiteConfig#setReadOnly and SQLiteConfig.createConnection().");
      }
    }
    readOnly = ro;
  }

  /** @see Connection#nativeSQL(String) */
  public String nativeSQL(String sql) {
    return sql;
  }

  /** @see Connection#clearWarnings() */
  public void clearWarnings() { }

  /** @see Connection#getWarnings() */
  public SQLWarning getWarnings() {
    return null;
  }

  /** @see Connection#createStatement() */
  public SqliteStatement createStatement() throws SQLException {
    return createStatement(
      ResultSet.TYPE_FORWARD_ONLY,
      ResultSet.CONCUR_READ_ONLY,
      ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /** @see Connection#createStatement(int, int) */
  public SqliteStatement createStatement(int rsType, int rsConcurr) throws SQLException {
    return createStatement(rsType, rsConcurr, ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /** @see Connection#prepareCall(String) */
  public CallableStatement prepareCall(String sql) throws SQLException {
    return prepareCall(
      sql,
      ResultSet.TYPE_FORWARD_ONLY,
      ResultSet.CONCUR_READ_ONLY,
      ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /** @see Connection#prepareCall(String, int, int) */
  public CallableStatement prepareCall(String sql, int rst, int rsc) throws SQLException {
    return prepareCall(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /** @see Connection#prepareCall(String, int, int, int) */
  public CallableStatement prepareCall(String sql, int rst, int rsc, int rsh)
    throws SQLException {
    throw new SQLException("SQLite does not support Stored Procedures");
  }

  /** @see Connection#prepareStatement(String) */
  public SqlitePreparedStatement prepareStatement(String sql) throws SQLException {
    return prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
  }

  /** @see Connection#prepareStatement(String, int) */
  public SqlitePreparedStatement prepareStatement(String sql, int autoC) throws SQLException {
    return prepareStatement(sql);
  }

  /** @see Connection#prepareStatement(String, int[]) */
  public SqlitePreparedStatement prepareStatement(String sql, int[] colInds) throws SQLException {
    return prepareStatement(sql);
  }

  /** @see Connection#prepareStatement(String, String[]) */
  public SqlitePreparedStatement prepareStatement(String sql, String[] colNames) throws SQLException {
    return prepareStatement(sql);
  }

  /** @see Connection#prepareStatement(String, int, int) */
  public SqlitePreparedStatement prepareStatement(String sql, int rst, int rsc) throws SQLException {
    return prepareStatement(sql, rst, rsc, ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /** @see Connection#setSavepoint() */
  public SqliteSavepoint setSavepoint() throws SQLException {
    checkOpen();
    var sp = new SqliteSavepoint(savePoint.incrementAndGet());
    getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
    return sp;
  }

  /** @see Connection#setSavepoint(String) */
  public SqliteSavepoint setSavepoint(String name) throws SQLException {
    checkOpen();
    var sp = new SqliteSavepoint(savePoint.incrementAndGet(), name);
    getDatabase().exec(String.format("SAVEPOINT %s", sp.getSavepointName()));
    return sp;
  }

  /** @see Connection#releaseSavepoint(Savepoint) */
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    checkOpen();
    getDatabase()
      .exec(String.format("RELEASE SAVEPOINT %s", savepoint.getSavepointName()));
  }

  /** @see Connection#rollback(Savepoint) */
  public void rollback(Savepoint savepoint) throws SQLException {
    checkOpen();
    getDatabase()
      .exec(
        String.format("ROLLBACK TO SAVEPOINT %s", savepoint.getSavepointName())
      );
  }

  public SQLiteConfig.TransactionMode getCurrentTransactionMode() {
    return currentTransactionMode;
  }

  public void setCurrentTransactionMode(final SQLiteConfig.TransactionMode currentTransactionMode) {
    this.currentTransactionMode = currentTransactionMode;
  }

  public boolean isFirstStatementExecuted() {
    return firstStatementExecuted;
  }

  public void setFirstStatementExecuted(final boolean firstStatementExecuted) {
    this.firstStatementExecuted = firstStatementExecuted;
  }

  public SQLiteConnectionConfig getConnectionConfig() {
    return connectionConfig;
  }

  /**
   * Checks whether the type, concurrency, and holdability settings for a {@link ResultSet} are
   * supported by the SQLite interface. Supported settings are:
   *
   * <ul>
   *   <li>type: {@link ResultSet#TYPE_FORWARD_ONLY}
   *   <li>concurrency: {@link ResultSet#CONCUR_READ_ONLY})
   *   <li>holdability: {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}
   * </ul>
   *
   * @param rst the type setting.
   * @param rsc the concurrency setting.
   * @param rsh the holdability setting.
   */
  private static void checkCursor(int rst, int rsc, int rsh) throws SQLException {
    if (rst != ResultSet.TYPE_FORWARD_ONLY) {
      throw new SQLException("SQLite only supports TYPE_FORWARD_ONLY cursors");
    }
    if (rsc != ResultSet.CONCUR_READ_ONLY) {
      throw new SQLException("SQLite only supports CONCUR_READ_ONLY cursors");
    }
    if (rsh != ResultSet.CLOSE_CURSORS_AT_COMMIT) {
      throw new SQLException("SQLite only supports closing cursors at commit");
    }
  }

  /** @see Connection#getTransactionIsolation() */
  public int getTransactionIsolation() {
    return connectionConfig.getTransactionIsolation();
  }

  /** @see Connection#setTransactionIsolation(int) */
  public void setTransactionIsolation(int level) throws SQLException {
    checkOpen();

    switch (level) {
      // Fall-through: Spec allows upgrading isolation to a higher level
      case Connection.TRANSACTION_READ_COMMITTED, Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE ->
        getDatabase().exec("PRAGMA read_uncommitted = false;");
      case Connection.TRANSACTION_READ_UNCOMMITTED -> getDatabase().exec("PRAGMA read_uncommitted = true;");
      default -> throw new SQLException(
        "Unsupported transaction isolation level: "
        + level
        + ". "
        + "Must be one of TRANSACTION_READ_UNCOMMITTED, TRANSACTION_READ_COMMITTED, "
        + "TRANSACTION_REPEATABLE_READ, or TRANSACTION_SERIALIZABLE in java.sql.Connection");
    }
    connectionConfig.setTransactionIsolation(level);
  }

  public DB getDatabase() {
    return db;
  }

  /**
   * @return The busy timeout value for the connection.
   * @see <a
   * href="http://www.sqlite.org/c3ref/busy_timeout.html">http://www.sqlite.org/c3ref/busy_timeout.html</a>
   */
  public int getBusyTimeout() {
    return db.getConfig().getBusyTimeout();
  }

  /**
   * Sets the timeout value for the connection. A timeout value less than or equal to zero turns
   * off all busy handlers.
   *
   * @param timeoutMillis The timeout value in milliseconds.
   * @see <a
   * href="http://www.sqlite.org/c3ref/busy_timeout.html">http://www.sqlite.org/c3ref/busy_timeout.html</a>
   */
  public void setBusyTimeout(int timeoutMillis) {
    db.getConfig().setBusyTimeout(timeoutMillis);
    db.busy_timeout(timeoutMillis);
  }

  public void setLimit(SQLiteLimits limit, int value) throws SQLException {
    // Calling sqlite3_limit with a negative number is a no-op:
    // https://www.sqlite.org/c3ref/limit.html
    if (value >= 0) {
      db.limit(limit.getId(), value);
    }
  }

  public void getLimit(SQLiteLimits limit) throws SQLException {
    db.limit(limit.getId(), -1);
  }

  /** @see Connection#close() */
  public void close() throws SQLException {
    if (isClosed()) return;

    db.close();
  }

  /**
   * Whether an SQLite library interface to the database has been established.
   *
   */
  private void checkOpen() throws SQLException {
    if (isClosed()) throw new SQLException("database connection closed");
  }

  /**
   * @return Compile-time library version numbers.
   * @see <a
   * href="http://www.sqlite.org/c3ref/c_source_id.html">http://www.sqlite.org/c3ref/c_source_id.html</a>
   */
  public String libversion() throws SQLException {
    checkOpen();

    return db.libversion();
  }

  /** @see Connection#commit() */
  public void commit() throws SQLException {
    checkOpen();
    db.exec("commit;");
    db.exec(transactionPrefix());
    firstStatementExecuted = false;
    setCurrentTransactionMode(getConnectionConfig().getTransactionMode());
  }

  /** @see Connection#rollback() */
  public void rollback() throws SQLException {
    checkOpen();
    db.exec("rollback;");
    db.exec(transactionPrefix());
    firstStatementExecuted = false;
    setCurrentTransactionMode(getConnectionConfig().getTransactionMode());
  }

  /**
   * Add a listener for DB update events, see <a href="https://www.sqlite.org/c3ref/update_hook.html">...</a>
   *
   * @param listener The listener to receive update events
   */
  public void addUpdateListener(SQLiteUpdateListener listener) {
    db.addUpdateListener(listener);
  }

  /**
   * Remove a listener registered for DB update events.
   *
   * @param listener The listener to no longer receive update events
   */
  public void removeUpdateListener(SQLiteUpdateListener listener) {
    db.removeUpdateListener(listener);
  }

  /**
   * Add a listener for DB commit/rollback events, see
   * <a href="https://www.sqlite.org/c3ref/commit_hook.html">...</a>
   *
   * @param listener The listener to receive commit events
   */
  public void addCommitListener(SQLiteCommitListener listener) {
    db.addCommitListener(listener);
  }

  /**
   * Remove a listener registered for DB commit/rollback events.
   *
   * @param listener The listener to no longer receive commit/rollback events.
   */
  public void removeCommitListener(SQLiteCommitListener listener) {
    db.removeCommitListener(listener);
  }

  private String transactionPrefix() {
    return connectionConfig.transactionPrefix();
  }

  /**
   * Opens a connection to the database using an SQLite library. * @throws SQLException
   *
   * @see <a
   * href="http://www.sqlite.org/c3ref/c_open_autoproxy.html">http://www.sqlite.org/c3ref/c_open_autoproxy.html</a>
   */
  private static DB open(String url, String fileName, SQLiteConfig config) throws SQLException {
    // check the path to the file exists
    if (!fileName.isEmpty()
        && !":memory:".equals(fileName)
        && !fileName.startsWith("file:")
        && !fileName.contains("mode=memory")) {
      File file = new File(fileName).getAbsoluteFile();
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        for (File up = parent; up != null && !up.exists(); ) {
          parent = up;
          up = up.getParentFile();
        }
        throw new SQLException("path to '" + fileName + "': '" + parent + "' does not exist");
      }

      // check write access if file does not exist
      try {
        // The extra check to exists() is necessary as createNewFile()
        // does not follow the JavaDoc when used on read-only shares.
        if (!file.exists() && file.createNewFile()) {
          file.delete();
        }
      }
      catch (Exception e) {
        throw new SQLException("opening db: '" + fileName + "': " + e.getMessage());
      }
      fileName = file.getAbsolutePath();
    }

    // load the native DB
    DB db;
    try {
      NativeDB.load();
      db = new NativeDB(url, fileName, config);
    }
    catch (Exception e) {
      throw new SQLException("Error opening connection", e);
    }
    db.open(fileName, config.getOpenModeFlags());
    return db;
  }
}
