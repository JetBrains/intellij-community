// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite.core;

import org.jetbrains.sqlite.SQLiteConnectionConfig;
import org.jetbrains.sqlite.date.FastDateFormat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqliteResultSet implements Codes {
  /** Pattern used to extract the column type name from table column definition. */
  private static final Pattern COLUMN_TYPENAME = Pattern.compile("([^\\(]*)");
  /** Pattern used to extract the column type name from a cast(col as type) */
  private static final Pattern COLUMN_TYPECAST = Pattern.compile("cast\\(.*?\\s+as\\s+(.*?)\\s*\\)");
  /**
   * Pattern used to extract the precision and scale from column meta returned by the JDBC driver.
   */
  private static final Pattern COLUMN_PRECISION = Pattern.compile(".*?\\((.*?)\\)");
  private final SqliteStatement stmt;
  /** If the result set does not have any rows. */
  boolean emptyResultSet = false;
  /** If the result set is open. Doesn't mean it has results. */
  boolean open = false;
  /** Maximum number of rows as set by a Statement */
  long maxRows;
  /** if null, the RS is closed() */
  String[] cols = null;
  /** same as cols, but used by Meta interface */
  String[] colsMeta = null;
  boolean closeStmt;
  private boolean[][] meta = null;
  /** 0 means no limit, must check against maxRows */
  private int limitRows;
  /** number of current row, starts at 1 (0 is for before loading data) */
  private int row = 0;
  private boolean pastLastRow = false;
  /** last column accessed, for wasNull(). -1 if none */
  private int lastCol;
  private Map<String, Integer> columnNameToIndex = null;

  public SqliteResultSet(SqliteStatement stmt) {
    this.stmt = stmt;
  }

  public void close() throws SQLException {
    final boolean wasOpen = isOpen(); // prevent close() recursion
    cols = null;
    colsMeta = null;
    meta = null;
    limitRows = 0;
    row = 0;
    pastLastRow = false;
    lastCol = -1;
    columnNameToIndex = null;
    emptyResultSet = false;

    if (!stmt.pointer.isClosed() && (open || closeStmt)) {
      DB db = stmt.getDatabase();
      synchronized (db) {
        if (!stmt.pointer.isClosed()) {
          stmt.pointer.safeRunInt(DB::reset);

          if (closeStmt) {
            closeStmt = false; // break recursive call
            stmt.close();
          }
        }
      }
      open = false;
    }

    // close-on-completion regardless of closeStmt
    if (wasOpen) {
      SqliteStatement stat = stmt;
      // check if its not closed already in which case no-op
      if (stat.closeOnCompletion && !stat.isClosed()) {
        stat.close();
      }
    }
  }
  public boolean isClosed() {
    return !isOpen();
  }

  public Reader getNCharacterStream(int col) throws SQLException {
    String data = getString(col);
    return getNCharacterStreamInternal(data);
  }

  private DB getDatabase() {
    return stmt.getDatabase();
  }

  private SQLiteConnectionConfig getConnectionConfig() {
    return stmt.getConnectionConfig();
  }

  /**
   * Checks the status of the result set.
   *
   * @return True if has results and can iterate them; false otherwise.
   */
  public boolean isOpen() {
    return open;
  }

  /** @throws SQLException if ResultSet is not open. */
  private void checkOpen() throws SQLException {
    if (!open) {
      throw new SQLException("ResultSet closed");
    }
  }

  /**
   * Takes col in [1,x] form, returns in [0,x-1] form
   *
   */
  public int checkCol(int col) throws SQLException {
    if (colsMeta == null) {
      throw new SQLException("SQLite JDBC: inconsistent internal state");
    }
    if (col < 1 || col > colsMeta.length) {
      throw new SQLException("column " + col + " out of bounds [1," + colsMeta.length + "]");
    }
    return --col;
  }

  /**
   * Takes col in [1,x] form, marks it as last accessed and returns [0,x-1]
   *
   */
  private int markCol(int col) throws SQLException {
    checkCol(col);
    lastCol = col;
    return --col;
  }

  /** */
  public void checkMeta() throws SQLException {
    checkCol(1);
    if (meta == null) {
      meta = stmt.pointer.safeRun(DB::column_metadata);
    }
  }

  private Integer findColumnIndexInCache(String col) {
    if (columnNameToIndex == null) {
      return null;
    }
    return columnNameToIndex.get(col);
  }

  private int addColumnIndexInCache(String col, int index) {
    if (columnNameToIndex == null) {
      columnNameToIndex = new HashMap<>(cols.length);
    }
    columnNameToIndex.put(col, index);
    return index;
  }

  private static Reader getNCharacterStreamInternal(String data) {
    if (data == null) {
      return null;
    }
    return new StringReader(data);
  }

  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    if (type == null) throw new SQLException("requested type cannot be null");
    if (type == String.class) return type.cast(getString(columnIndex));
    if (type == Boolean.class) return type.cast(getBoolean(columnIndex));
    if (type == BigDecimal.class) return type.cast(getBigDecimal(columnIndex));
    if (type == byte[].class) return type.cast(getBytes(columnIndex));
    if (type == Date.class) return type.cast(getDate(columnIndex));
    if (type == Time.class) return type.cast(getTime(columnIndex));
    if (type == Timestamp.class) return type.cast(getTimestamp(columnIndex));

    int columnType = safeGetColumnType(markCol(columnIndex));
    if (type == Double.class) {
      if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT) {
        return type.cast(getDouble(columnIndex));
      }
      throw new SQLException("Bad value for type Double");
    }
    if (type == Long.class) {
      if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT) {
        return type.cast(getLong(columnIndex));
      }
      throw new SQLException("Bad value for type Long");
    }
    if (type == Float.class) {
      if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT) {
        return type.cast(getFloat(columnIndex));
      }
      throw new SQLException("Bad value for type Float");
    }
    if (type == Integer.class) {
      if (columnType == SQLITE_INTEGER || columnType == SQLITE_FLOAT) {
        return type.cast(getInt(columnIndex));
      }
      throw new SQLException("Bad value for type Integer");
    }

    throw unsupported();
  }

  private static SQLException unsupported() {
    return new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
  }

  // ResultSet ////////////////////////////////////////////////////

  public Array getArray(int i) throws SQLException {
    throw unsupported();
  }

  public InputStream getAsciiStream(int col) throws SQLException {
    String data = getString(col);
    return getAsciiStreamInternal(data);
  }

  private static InputStream getAsciiStreamInternal(String data) {
    if (data == null) {
      return null;
    }
    InputStream inputStream;
    inputStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.US_ASCII));
    return inputStream;
  }


  public InputStream getUnicodeStream(int col) throws SQLException {
    return getAsciiStream(col);
  }

  /** @see ResultSet#next() */
  public boolean next() throws SQLException {
    if (!open || emptyResultSet || pastLastRow) {
      return false; // finished ResultSet
    }
    lastCol = -1;

    // first row is loaded by execute(), so do not step() again
    if (row == 0) {
      row++;
      return true;
    }

    // check if we are row limited by the statement or the ResultSet
    if (maxRows != 0 && row == maxRows) {
      return false;
    }

    // do the real work
    int statusCode = stmt.pointer.safeRunInt(DB::step);
    switch (statusCode) {
      case SQLITE_DONE -> {
        pastLastRow = true;
        return false;
      }
      case SQLITE_ROW -> {
        row++;
        return true;
      }
      default -> {
        getDatabase().throwex(statusCode);
        return false;
      }
    }
  }

  /** @see ResultSet#getType() */
  public int getType() {
    return ResultSet.TYPE_FORWARD_ONLY;
  }

  /** @see ResultSet#getFetchSize() */
  public int getFetchSize() {
    return limitRows;
  }

  /** @see ResultSet#setFetchSize(int) */
  public void setFetchSize(int rows) throws SQLException {
    if (0 > rows || (maxRows != 0 && rows > maxRows)) {
      throw new SQLException("fetch size " + rows + " out of bounds " + maxRows);
    }
    limitRows = rows;
  }

  /** @see ResultSet#getFetchDirection() */
  public int getFetchDirection() throws SQLException {
    checkOpen();
    return ResultSet.FETCH_FORWARD;
  }

  /** @see ResultSet#setFetchDirection(int) */
  public void setFetchDirection(int d) throws SQLException {
    checkOpen();
    // Only FORWARD_ONLY ResultSets exist in SQLite, so only FETCH_FORWARD is permitted
    if (
      /*getType() == ResultSet.TYPE_FORWARD_ONLY &&*/
      d != ResultSet.FETCH_FORWARD) {
      throw new SQLException("only FETCH_FORWARD direction supported");
    }
  }

  /** @see ResultSet#isAfterLast() */
  public boolean isAfterLast() {
    return pastLastRow && !emptyResultSet;
  }

  /** @see ResultSet#isBeforeFirst() */
  public boolean isBeforeFirst() {
    return !emptyResultSet && open && row == 0;
  }

  /** @see ResultSet#isFirst() */
  public boolean isFirst() {
    return row == 1;
  }

  /** @see ResultSet#getRow() */
  public int getRow() {
    return row;
  }

  /** @see ResultSet#wasNull() */
  public boolean wasNull() throws SQLException {
    return safeGetColumnType(markCol(lastCol)) == SQLITE_NULL;
  }

  /** @see ResultSet#getBigDecimal(int) */
  public BigDecimal getBigDecimal(int col) throws SQLException {
    switch (safeGetColumnType(checkCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_FLOAT -> {
        return BigDecimal.valueOf(safeGetDoubleCol(col));
      }
      case SQLITE_INTEGER -> {
        return BigDecimal.valueOf(safeGetLongCol(col));
      }
      default -> {
        final String stringValue = safeGetColumnText(col);
        try {
          return new BigDecimal(stringValue);
        }
        catch (NumberFormatException e) {
          throw new SQLException("Bad value for type BigDecimal : " + stringValue);
        }
      }
    }
  }

  /** @see ResultSet#getBoolean(int) */
  public boolean getBoolean(int col) throws SQLException {
    return getInt(col) != 0;
  }

  /** @see ResultSet#getBinaryStream(int) */
  public InputStream getBinaryStream(int col) throws SQLException {
    byte[] bytes = getBytes(col);
    if (bytes != null) {
      return new ByteArrayInputStream(bytes);
    }
    else {
      return null;
    }
  }

  /** @see ResultSet#getByte(int) */
  public byte getByte(int col) throws SQLException {
    return (byte)getInt(col);
  }

  /** @see ResultSet#getBytes(int) */
  public byte[] getBytes(int col) throws SQLException {
    return stmt.pointer.safeRun((db, ptr) -> db.column_blob(ptr, markCol(col)));
  }

  /** @see ResultSet#getCharacterStream(int) */
  public Reader getCharacterStream(int col) throws SQLException {
    String string = getString(col);
    return string == null ? null : new StringReader(string);
  }

  /** @see ResultSet#getDate(int) */
  public Date getDate(int col) throws SQLException {
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          return new Date(
            getConnectionConfig().getDateFormat().parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing date", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Date(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER:
        return new Date(safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
      }
    }
  }

  /** @see ResultSet#getDate(int, Calendar) */
  public Date getDate(int col, Calendar cal) throws SQLException {
    requireCalendarNotNull(cal);
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          FastDateFormat dateFormat =
            FastDateFormat.getInstance(
              getConnectionConfig().getDateStringFormat(), cal.getTimeZone());

          return new Date(dateFormat.parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing time stamp", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Date(julianDateToCalendar(safeGetDoubleCol(col), cal).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER:
        cal.setTimeInMillis(
          safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        return new Date(cal.getTime().getTime());
      }
    }
  }

  /** @see ResultSet#getDouble(int) */
  public double getDouble(int col) throws SQLException {
    if (safeGetColumnType(markCol(col)) == SQLITE_NULL) {
      return 0;
    }
    return safeGetDoubleCol(col);
  }

  /** @see ResultSet#getFloat(int) */
  public float getFloat(int col) throws SQLException {
    if (safeGetColumnType(markCol(col)) == SQLITE_NULL) {
      return 0;
    }
    return (float)safeGetDoubleCol(col);
  }

  /** @see ResultSet#getInt(int) */
  public int getInt(int col) throws SQLException {
    return stmt.pointer.safeRunInt((db, ptr) -> db.column_int(ptr, markCol(col)));
  }

  /** @see ResultSet#getLong(int) */
  public long getLong(int col) throws SQLException {
    return safeGetLongCol(col);
  }

  /** @see ResultSet#getShort(int) */
  public short getShort(int col) throws SQLException {
    return (short)getInt(col);
  }

  /** @see ResultSet#getString(int) */
  public String getString(int col) throws SQLException {
    return safeGetColumnText(col);
  }

  /** @see ResultSet#getTime(int) */
  public Time getTime(int col) throws SQLException {
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          return new Time(
            getConnectionConfig().getDateFormat().parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing time", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Time(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER
        return new Time(safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
      }
    }
  }

  /** @see ResultSet#getTime(int, Calendar) */
  public Time getTime(int col, Calendar cal) throws SQLException {
    requireCalendarNotNull(cal);
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          FastDateFormat dateFormat =
            FastDateFormat.getInstance(
              getConnectionConfig().getDateStringFormat(), cal.getTimeZone());

          return new Time(dateFormat.parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing time", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Time(julianDateToCalendar(safeGetDoubleCol(col), cal).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER
        cal.setTimeInMillis(
          safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        return new Time(cal.getTime().getTime());
      }
    }
  }

  /** @see ResultSet#getTimestamp(int) */
  public Timestamp getTimestamp(int col) throws SQLException {
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          return new Timestamp(
            getConnectionConfig().getDateFormat().parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing time stamp", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Timestamp(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER:
        return new Timestamp(
          safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
      }
    }
  }

  /** @see ResultSet#getTimestamp(int, Calendar) */
  public Timestamp getTimestamp(int col, Calendar cal) throws SQLException {
    requireCalendarNotNull(cal);
    switch (safeGetColumnType(markCol(col))) {
      case SQLITE_NULL -> {
        return null;
      }
      case SQLITE_TEXT -> {
        String dateText = safeGetColumnText(col);
        if ("".equals(dateText)) {
          return null;
        }
        try {
          FastDateFormat dateFormat =
            FastDateFormat.getInstance(
              getConnectionConfig().getDateStringFormat(), cal.getTimeZone());

          return new Timestamp(dateFormat.parse(dateText).getTime());
        }
        catch (Exception e) {
          throw new SQLException("Error parsing time stamp", e);
        }
      }
      case SQLITE_FLOAT -> {
        return new Timestamp(julianDateToCalendar(safeGetDoubleCol(col)).getTimeInMillis());
      }
      default -> { // SQLITE_INTEGER
        cal.setTimeInMillis(
          safeGetLongCol(col) * getConnectionConfig().getDateMultiplier());
        return new Timestamp(cal.getTime().getTime());
      }
    }
  }

  /** @see ResultSet#getObject(int) */
  public Object getObject(int col) throws SQLException {
    return switch (safeGetColumnType(markCol(col))) {
      case SQLITE_INTEGER -> {
        long val = getLong(col);
        if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
          yield Long.valueOf(val);
        }
        else {
          yield Integer.valueOf((int)val);
        }
      }
      case SQLITE_FLOAT -> Double.valueOf(getDouble(col));
      case SQLITE_BLOB -> getBytes(col);
      case SQLITE_NULL -> null;
      default -> getString(col);
    };
  }

  /** @see ResultSetMetaData#getColumnCount() */
  public int getColumnCount() throws SQLException {
    checkCol(1);
    return colsMeta.length;
  }

  /** @see ResultSetMetaData#getColumnDisplaySize(int) */
  public int getColumnDisplaySize(int col) {
    return Integer.MAX_VALUE;
  }

  /** @see ResultSetMetaData#getColumnLabel(int) */
  public String getColumnLabel(int col) throws SQLException {
    return getColumnName(col);
  }

  /** @see ResultSetMetaData#getColumnName(int) */
  public String getColumnName(int col) throws SQLException {
    return safeGetColumnName(col);
  }

  /** @see ResultSetMetaData#getColumnType(int) */
  public int getColumnType(int col) throws SQLException {
    String typeName = getColumnTypeName(col);
    int valueType = safeGetColumnType(checkCol(col));

    if (valueType == SQLITE_INTEGER || valueType == SQLITE_NULL) {
      if ("BOOLEAN".equals(typeName)) {
        return Types.BOOLEAN;
      }

      if ("TINYINT".equals(typeName)) {
        return Types.TINYINT;
      }

      if ("SMALLINT".equals(typeName) || "INT2".equals(typeName)) {
        return Types.SMALLINT;
      }

      if ("BIGINT".equals(typeName)
          || "INT8".equals(typeName)
          || "UNSIGNED BIG INT".equals(typeName)) {
        return Types.BIGINT;
      }

      if ("DATE".equals(typeName) || "DATETIME".equals(typeName)) {
        return Types.DATE;
      }

      if ("TIMESTAMP".equals(typeName)) {
        return Types.TIMESTAMP;
      }

      if (valueType == SQLITE_INTEGER
          || "INT".equals(typeName)
          || "INTEGER".equals(typeName)
          || "MEDIUMINT".equals(typeName)) {
        long val = getLong(col);
        if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
          return Types.BIGINT;
        }
        else {
          return Types.INTEGER;
        }
      }
    }

    if (valueType == SQLITE_FLOAT || valueType == SQLITE_NULL) {
      if ("DECIMAL".equals(typeName)) {
        return Types.DECIMAL;
      }

      if ("DOUBLE".equals(typeName) || "DOUBLE PRECISION".equals(typeName)) {
        return Types.DOUBLE;
      }

      if ("NUMERIC".equals(typeName)) {
        return Types.NUMERIC;
      }

      if ("REAL".equals(typeName)) {
        return Types.REAL;
      }

      if (valueType == SQLITE_FLOAT || "FLOAT".equals(typeName)) {
        return Types.FLOAT;
      }
    }

    if (valueType == SQLITE_TEXT || valueType == SQLITE_NULL) {
      if ("CHARACTER".equals(typeName)
          || "NCHAR".equals(typeName)
          || "NATIVE CHARACTER".equals(typeName)
          || "CHAR".equals(typeName)) {
        return Types.CHAR;
      }

      if ("CLOB".equals(typeName)) {
        return Types.CLOB;
      }

      if ("DATE".equals(typeName) || "DATETIME".equals(typeName)) {
        return Types.DATE;
      }

      if ("TIMESTAMP".equals(typeName)) {
        return Types.TIMESTAMP;
      }

      if (valueType == SQLITE_TEXT
          || "VARCHAR".equals(typeName)
          || "VARYING CHARACTER".equals(typeName)
          || "NVARCHAR".equals(typeName)
          || "TEXT".equals(typeName)) {
        return Types.VARCHAR;
      }
    }

    if (valueType == SQLITE_BLOB || valueType == SQLITE_NULL) {
      if ("BINARY".equals(typeName)) {
        return Types.BINARY;
      }

      if (valueType == SQLITE_BLOB || "BLOB".equals(typeName)) {
        return Types.BLOB;
      }
    }

    return Types.NUMERIC;
  }

  /**
   * @return The data type from either the 'create table' statement, or CAST(expr AS TYPE)
   * otherwise sqlite3_value_type.
   * @see ResultSetMetaData#getColumnTypeName(int)
   */
  public String getColumnTypeName(int col) throws SQLException {
    String declType = getColumnDeclType(col);

    if (declType != null) {
      Matcher matcher = COLUMN_TYPENAME.matcher(declType);

      matcher.find();
      return matcher.group(1).toUpperCase(Locale.ENGLISH);
    }

    return switch (safeGetColumnType(checkCol(col))) {
      case SQLITE_INTEGER -> "INTEGER";
      case SQLITE_FLOAT -> "FLOAT";
      case SQLITE_BLOB -> "BLOB";
      case SQLITE_TEXT -> "TEXT";
      default -> "NUMERIC";
    };
  }

  /** @see ResultSetMetaData#getPrecision(int) */
  public int getPrecision(int col) throws SQLException {
    String declType = getColumnDeclType(col);

    if (declType != null) {
      Matcher matcher = COLUMN_PRECISION.matcher(declType);

      return matcher.find() ? Integer.parseInt(matcher.group(1).split(",")[0].trim()) : 0;
    }

    return 0;
  }

  private String getColumnDeclType(int col) throws SQLException {
    String declType = stmt.pointer.safeRun((db, ptr) -> db.column_decltype(ptr, checkCol(col)));

    if (declType == null) {
      Matcher matcher = COLUMN_TYPECAST.matcher(safeGetColumnName(col));
      declType = matcher.find() ? matcher.group(1) : null;
    }

    return declType;
  }

  /** @see ResultSetMetaData#getScale(int) */
  public int getScale(int col) throws SQLException {
    String declType = getColumnDeclType(col);

    if (declType != null) {
      Matcher matcher = COLUMN_PRECISION.matcher(declType);

      if (matcher.find()) {
        String[] array = matcher.group(1).split(",");

        if (array.length == 2) {
          return Integer.parseInt(array[1].trim());
        }
      }
    }

    return 0;
  }


  /** @see ResultSetMetaData#getTableName(int) */
  public String getTableName(int col) throws SQLException {
    final String tableName = safeGetColumnTableName(col);
    if (tableName == null) {
      // JDBC specifies an empty string instead of null
      return "";
    }
    return tableName;
  }

  /** @see ResultSetMetaData#isNullable(int) */
  public int isNullable(int col) throws SQLException {
    checkMeta();
    return meta[checkCol(col)][0]
           ? ResultSetMetaData.columnNoNulls
           : ResultSetMetaData.columnNullable;
  }

  /** @see ResultSetMetaData#isAutoIncrement(int) */
  public boolean isAutoIncrement(int col) throws SQLException {
    checkMeta();
    return meta[checkCol(col)][2];
  }

  /** @see ResultSetMetaData#isDefinitelyWritable(int) */
  public boolean isDefinitelyWritable(int col) {
    return true;
  } // FIXME: check db file constraints?

  /** @see ResultSetMetaData#isReadOnly(int) */
  public boolean isReadOnly(int col) {
    return false;
  }

  /** @see ResultSetMetaData#isSearchable(int) */
  public boolean isSearchable(int col) {
    return true;
  }

  /** @see ResultSetMetaData#isSigned(int) */
  public boolean isSigned(int col) throws SQLException {
    String typeName = getColumnTypeName(col);

    return "NUMERIC".equals(typeName) || "INTEGER".equals(typeName) || "REAL".equals(typeName);
  }

  /** @see ResultSet#getConcurrency() */
  public int getConcurrency() {
    return ResultSet.CONCUR_READ_ONLY;
  }

  /** @see ResultSet#rowDeleted() */
  public boolean rowDeleted() {
    return false;
  }

  /** @see ResultSet#rowInserted() */
  public boolean rowInserted() {
    return false;
  }

  /** @see ResultSet#rowUpdated() */
  public boolean rowUpdated() {
    return false;
  }

  /** Transforms a Julian Date to java.util.Calendar object. */
  private static Calendar julianDateToCalendar(Double jd) {
    return julianDateToCalendar(jd, Calendar.getInstance());
  }

  /**
   * Transforms a Julian Date to java.util.Calendar object. Based on Guine Christian's function
   * found here:
   * <a href="https://java.ittoolbox.com/groups/technical-functional/java-l/java-function-to-convert-julian-date-to-calendar-date-1947446">...</a>
   */
  private static Calendar julianDateToCalendar(Double jd, Calendar cal) {
    if (jd == null) {
      return null;
    }

    int yyyy, dd, mm, hh, mn, ss, ms, A;

    double w = jd + 0.5;
    int Z = (int)w;
    double F = w - Z;

    if (Z < 2299161) {
      A = Z;
    }
    else {
      int alpha = (int)((Z - 1867216.25) / 36524.25);
      A = Z + 1 + alpha - (int)(alpha / 4.0);
    }

    int B = A + 1524;
    int C = (int)((B - 122.1) / 365.25);
    int D = (int)(365.25 * C);
    int E = (int)((B - D) / 30.6001);

    //  month
    mm = E - ((E < 13.5) ? 1 : 13);

    // year
    yyyy = C - ((mm > 2.5) ? 4716 : 4715);

    // Day
    double jjd = B - D - (int)(30.6001 * E) + F;
    dd = (int)jjd;

    // Hour
    double hhd = jjd - dd;
    hh = (int)(24 * hhd);

    // Minutes
    double mnd = (24 * hhd) - hh;
    mn = (int)(60 * mnd);

    // Seconds
    double ssd = (60 * mnd) - mn;
    ss = (int)(60 * ssd);

    // Milliseconds
    double msd = (60 * ssd) - ss;
    ms = (int)(1000 * msd);

    cal.set(yyyy, mm - 1, dd, hh, mn, ss);
    cal.set(Calendar.MILLISECOND, ms);

    if (yyyy < 1) {
      cal.set(Calendar.ERA, GregorianCalendar.BC);
      cal.set(Calendar.YEAR, -(yyyy - 1));
    }

    return cal;
  }

  private static void requireCalendarNotNull(Calendar cal) throws SQLException {
    if (cal == null) {
      throw new SQLException("Expected a calendar instance.", new IllegalArgumentException());
    }
  }

  private int safeGetColumnType(int col) throws SQLException {
    return stmt.pointer.safeRunInt((db, ptr) -> db.column_type(ptr, col));
  }

  private long safeGetLongCol(int col) throws SQLException {
    return stmt.pointer.safeRunLong((db, ptr) -> db.column_long(ptr, markCol(col)));
  }

  private double safeGetDoubleCol(int col) throws SQLException {
    return stmt.pointer.safeRunDouble((db, ptr) -> db.column_double(ptr, markCol(col)));
  }

  private String safeGetColumnText(int col) throws SQLException {
    return stmt.pointer.safeRun((db, ptr) -> db.column_text(ptr, markCol(col)));
  }

  private String safeGetColumnTableName(int col) throws SQLException {
    return stmt.pointer.safeRun((db, ptr) -> db.column_table_name(ptr, checkCol(col)));
  }

  private String safeGetColumnName(int col) throws SQLException {
    return stmt.pointer.safeRun((db, ptr) -> db.column_name(ptr, checkCol(col)));
  }
}
