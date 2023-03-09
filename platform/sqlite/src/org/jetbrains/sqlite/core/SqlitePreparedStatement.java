// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite.core;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.sqlite.SQLiteConnectionConfig;
import org.jetbrains.sqlite.date.FastDateFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;
import java.util.Calendar;

public final class SqlitePreparedStatement extends SqliteStatement {
  private final int columnCount;
  private final int paramCount;
  private int batchQueryCount;

  public SqlitePreparedStatement(SqliteConnection conn, String sql) throws SQLException {
    super(conn);

    this.sql = sql;
    DB db = conn.getDatabase();
    db.prepare(this, sql);
    rs.colsMeta = pointer.safeRun(DB::column_names);
    columnCount = pointer.safeRunInt(DB::column_count);
    paramCount = pointer.safeRunInt(DB::bind_parameter_count);
    batchQueryCount = 0;
    batch = null;
    batchPos = 0;
  }

  @Override
  public String toString() {
    return sql + " \n parameters=" + Arrays.toString(batch);
  }


  /** @see PreparedStatement#clearParameters() */
  public void clearParameters() throws SQLException {
    checkOpen();
    pointer.safeRunConsume(DB::clear_bindings);
    if (batch != null) for (int i = batchPos; i < batchPos + paramCount; i++) batch[i] = null;
  }

  /** @see PreparedStatement#execute() */
  public boolean execute() throws SQLException {
    checkOpen();
    rs.close();
    pointer.safeRunConsume(DB::reset);

    conn.tryEnforceTransactionMode();
    return withConnectionTimeout(
      () -> {
        boolean success = false;
        try {
          resultsWaiting = conn.getDatabase().execute(this, batch, sql);
          success = true;
          updateCount = getDatabase().changes();
          return 0 != columnCount;
        }
        finally {
          if (!success && !pointer.isClosed()) pointer.safeRunConsume(DB::reset);
        }
      });
  }

  /** @see PreparedStatement#executeQuery() */
  public SqliteResultSet executeQuery() throws SQLException {
    checkOpen();

    if (columnCount == 0) {
      throw new SQLException("Query does not return results");
    }

    rs.close();
    pointer.safeRunConsume(DB::reset);

    conn.tryEnforceTransactionMode();
    return withConnectionTimeout(() -> {
      boolean success = false;
      try {
        resultsWaiting = conn.getDatabase().execute(this, batch, sql);
        success = true;
      }
      finally {
        if (!success && !pointer.isClosed()) {
          pointer.safeRunInt(DB::reset);
        }
      }
      return getResultSet();
    });
  }

  /** @see PreparedStatement#executeUpdate() */
  public long executeUpdate(boolean collectChanges) throws SQLException {
    checkOpen();

    if (columnCount != 0) {
      throw new SQLException("Query returns results");
    }

    rs.close();
    pointer.safeRunConsume(DB::reset);

    conn.tryEnforceTransactionMode();
    return withConnectionTimeout(() -> conn.getDatabase().executeUpdate(this, batch, collectChanges));
  }

  public void addBatch() throws SQLException {
    checkOpen();
    batchPos += paramCount;
    batchQueryCount++;
    if (batch == null) {
      batch = new Object[paramCount];
    }
    if (batchPos + paramCount > batch.length) {
      Object[] nb = new Object[batch.length * 2];
      System.arraycopy(batch, 0, nb, 0, batch.length);
      batch = nb;
    }
    System.arraycopy(batch, batchPos - paramCount, batch, batchPos, paramCount);
  }

  /** @see ParameterMetaData#getParameterCount() */
  public int getParameterCount() throws SQLException {
    checkOpen();
    return paramCount;
  }

  /** @see ParameterMetaData#getParameterClassName(int) */
  public String getParameterClassName(int param) throws SQLException {
    checkOpen();
    return "java.lang.String";
  }

  /** @see ParameterMetaData#getParameterTypeName(int) */
  public String getParameterTypeName(int pos) {
    return "VARCHAR";
  }

  /** @see PreparedStatement#setBigDecimal(int, BigDecimal) */
  public void setBigDecimal(int pos, BigDecimal value) throws SQLException {
    batch(pos, value == null ? null : value.toString());
  }

  @Override
  public long[] executeBatch(boolean collectChanges) throws SQLException {
    if (batchQueryCount == 0) {
      return ArrayUtilRt.EMPTY_LONG_ARRAY;
    }

    conn.tryEnforceTransactionMode();
    return withConnectionTimeout(() -> {
      try {
        return pointer.safeRun((db, ptr) -> db.executeBatch(ptr, batchQueryCount, batch, collectChanges));
      }
      finally {
        clearBatch();
      }
    });
  }

  /** @see SqliteStatement#clearBatch() () */
  @Override
  public void clearBatch() throws SQLException {
    super.clearBatch();
    batchQueryCount = 0;
  }

  /**
   * Assigns the object value to the element at the specific position of the array batch.
   */
  private void batch(int pos, Object value) throws SQLException {
    checkOpen();
    if (batch == null) {
      batch = new Object[paramCount];
    }
    batch[batchPos + pos - 1] = value;
  }

  /** Store the date in the user's preferred format (text, int, or real) */
  private void setDateByMilliseconds(int pos, Long value, Calendar calendar)
    throws SQLException {
    SQLiteConnectionConfig config = conn.getConnectionConfig();
    switch (config.getDateClass()) {
      case TEXT -> batch(
        pos,
        FastDateFormat.getInstance(config.getDateStringFormat(), calendar.getTimeZone()).format(new Date(value)));
      case REAL ->
        // long to Julian date
        batch(pos, Double.valueOf((value / 86400000.0) + 2440587.5));
      default -> // INTEGER:
        batch(pos, Long.valueOf(value / config.getDateMultiplier()));
    }
  }

  /**
   * Reads given number of bytes from an input stream.
   *
   * @param input The input stream.
   * @param length  The number of bytes to read.
   * @return byte array.
   */
  private static byte[] readBytes(InputStream input, int length) throws SQLException {
    if (length < 0) {
      throw new SQLException("Error reading stream. Length should be non-negative");
    }

    byte[] bytes = new byte[length];

    try {
      int bytesRead;
      int totalBytesRead = 0;

      while (totalBytesRead < length) {
        bytesRead = input.read(bytes, totalBytesRead, length - totalBytesRead);
        if (bytesRead == -1) {
          throw new IOException("End of stream has been reached");
        }
        totalBytesRead += bytesRead;
      }

      return bytes;
    }
    catch (IOException cause) {

      throw new SQLException("Error reading stream", cause);
    }
  }

  /** @see PreparedStatement#setBinaryStream(int, InputStream, int) */
  public void setBinaryStream(int pos, InputStream input, int length) throws SQLException {
    if (input == null && length == 0) {
      setBytes(pos, null);
    }

    setBytes(pos, readBytes(input, length));
  }

  /** @see PreparedStatement#setAsciiStream(int, InputStream, int) */
  public void setAsciiStream(int pos, InputStream input, int length) throws SQLException {
    setUnicodeStream(pos, input, length);
  }

  public void setUnicodeStream(int pos, InputStream input, int length) throws SQLException {
    if (input == null && length == 0) {
      setString(pos, null);
    }

    setString(pos, new String(readBytes(input, length), StandardCharsets.UTF_8));
  }

  /** @see PreparedStatement#setBoolean(int, boolean) */
  public void setBoolean(int pos, boolean value) throws SQLException {
    setInt(pos, value ? 1 : 0);
  }

  /** @see PreparedStatement#setByte(int, byte) */
  public void setByte(int pos, byte value) throws SQLException {
    setInt(pos, value);
  }

  /** @see PreparedStatement#setBytes(int, byte[]) */
  public void setBytes(int pos, byte[] value) throws SQLException {
    batch(pos, value);
  }

  /** @see PreparedStatement#setDouble(int, double) */
  public void setDouble(int pos, double value) throws SQLException {
    batch(pos, Double.valueOf(value));
  }

  /** @see PreparedStatement#setFloat(int, float) */
  public void setFloat(int pos, float value) throws SQLException {
    batch(pos, Float.valueOf(value));
  }

  public void setInt(int pos, int value) throws SQLException {
    batch(pos, Integer.valueOf(value));
  }

  /** @see PreparedStatement#setLong(int, long) */
  public void setLong(int pos, long value) throws SQLException {
    batch(pos, Long.valueOf(value));
  }

  /** @see PreparedStatement#setNull(int, int) */
  public void setNull(int pos, int u1) throws SQLException {
    batch(pos, null);
  }

  /** @see PreparedStatement#setObject(int, Object) */
  public void setObject(int pos, Object value) throws SQLException {
    if (value == null) {
      batch(pos, null);
    }
    else if (value instanceof java.util.Date) {
      setDateByMilliseconds(pos, ((java.util.Date)value).getTime(), Calendar.getInstance());
    }
    else if (value instanceof Long) {
      batch(pos, value);
    }
    else if (value instanceof Integer) {
      batch(pos, value);
    }
    else if (value instanceof Short) {
      batch(pos, Integer.valueOf(((Short)value).intValue()));
    }
    else if (value instanceof Float) {
      batch(pos, value);
    }
    else if (value instanceof Double) {
      batch(pos, value);
    }
    else if (value instanceof Boolean) {
      setBoolean(pos, ((Boolean)value).booleanValue());
    }
    else if (value instanceof byte[]) {
      batch(pos, value);
    }
    else if (value instanceof BigDecimal) {
      setBigDecimal(pos, (BigDecimal)value);
    }
    else {
      batch(pos, value.toString());
    }
  }

  /** @see PreparedStatement#setObject(int, Object, int) */
  public void setObject(int p, Object v, int t) throws SQLException {
    setObject(p, v);
  }

  /** @see PreparedStatement#setObject(int, Object, int, int) */
  public void setObject(int p, Object v, int t, int s) throws SQLException {
    setObject(p, v);
  }

  /** @see PreparedStatement#setShort(int, short) */
  public void setShort(int pos, short value) throws SQLException {
    setInt(pos, value);
  }

  /** @see PreparedStatement#setString(int, String) */
  public void setString(int pos, String value) throws SQLException {
    batch(pos, value);
  }

  /** @see PreparedStatement#setCharacterStream(int, Reader, int) */
  public void setCharacterStream(int pos, Reader reader, int length) throws SQLException {
    try {
      // copy chars from reader to StringBuffer
      StringBuilder sb = new StringBuilder();
      char[] cbuf = new char[8192];
      int cnt;

      while ((cnt = reader.read(cbuf)) > 0) {
        sb.append(cbuf, 0, cnt);
      }

      // set as string
      setString(pos, sb.toString());
    }
    catch (IOException e) {
      throw new SQLException(
        "Cannot read from character stream, exception message: " + e.getMessage());
    }
  }

  /** @see PreparedStatement#setDate(int, Date) */
  public void setDate(int pos, Date x) throws SQLException {
    setDate(pos, x, Calendar.getInstance());
  }

  /** @see PreparedStatement#setDate(int, Date, Calendar) */
  public void setDate(int pos, Date x, Calendar cal) throws SQLException {
    if (x == null) {
      setObject(pos, null);
    }
    else {
      setDateByMilliseconds(pos, x.getTime(), cal);
    }
  }

  /** @see PreparedStatement#setTime(int, Time) */
  public void setTime(int pos, Time x) throws SQLException {
    setTime(pos, x, Calendar.getInstance());
  }

  /** @see PreparedStatement#setTime(int, Time, Calendar) */
  public void setTime(int pos, Time x, Calendar cal) throws SQLException {
    if (x == null) {
      setObject(pos, null);
    }
    else {
      setDateByMilliseconds(pos, x.getTime(), cal);
    }
  }

  /** @see PreparedStatement#setTimestamp(int, Timestamp) */
  public void setTimestamp(int pos, Timestamp x) throws SQLException {
    setTimestamp(pos, x, Calendar.getInstance());
  }

  /** @see PreparedStatement#setTimestamp(int, Timestamp, Calendar) */
  public void setTimestamp(int pos, Timestamp x, Calendar cal) throws SQLException {
    if (x == null) {
      setObject(pos, null);
    }
    else {
      setDateByMilliseconds(pos, x.getTime(), cal);
    }
  }

  private static SQLException invalid() {
    return new SQLException("method cannot be called on a PreparedStatement");
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    throw invalid();
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    throw invalid();
  }

  /**  */
  @Override
  public void addBatch(String sql) throws SQLException {
    throw invalid();
  }
}
