// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.sqlite;

import org.jetbrains.sqlite.date.FastDateFormat;

import java.sql.Connection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Connection local configurations */
public final class SQLiteConnectionConfig {
  private static final Map<SQLiteConfig.TransactionMode, String> beginCommandMap =
    new EnumMap<>(SQLiteConfig.TransactionMode.class);

  static {
    beginCommandMap.put(SQLiteConfig.TransactionMode.DEFERRED, "begin;");
    beginCommandMap.put(SQLiteConfig.TransactionMode.IMMEDIATE, "begin immediate;");
    beginCommandMap.put(SQLiteConfig.TransactionMode.EXCLUSIVE, "begin exclusive;");
  }

  private SQLiteConfig.DateClass dateClass = SQLiteConfig.DateClass.INTEGER;
  private SQLiteConfig.DatePrecision datePrecision =
    SQLiteConfig.DatePrecision.MILLISECONDS; // Calendar.SECOND or Calendar.MILLISECOND
  private String dateStringFormat = SQLiteConfig.DEFAULT_DATE_STRING_FORMAT;
  private FastDateFormat dateFormat = FastDateFormat.getInstance(dateStringFormat);
  private int transactionIsolation = Connection.TRANSACTION_SERIALIZABLE;
  private SQLiteConfig.TransactionMode transactionMode = SQLiteConfig.TransactionMode.DEFERRED;

  public SQLiteConnectionConfig(
    SQLiteConfig.DateClass dateClass,
    SQLiteConfig.DatePrecision datePrecision,
    String dateStringFormat,
    int transactionIsolation,
    SQLiteConfig.TransactionMode transactionMode) {
    setDateClass(dateClass);
    setDatePrecision(datePrecision);
    setDateStringFormat(dateStringFormat);
    setTransactionIsolation(transactionIsolation);
    setTransactionMode(transactionMode);
  }

  public SQLiteConnectionConfig copyConfig() {
    return new SQLiteConnectionConfig(
      dateClass,
      datePrecision,
      dateStringFormat,
      transactionIsolation,
      transactionMode
    );
  }

  public long getDateMultiplier() {
    return (datePrecision == SQLiteConfig.DatePrecision.MILLISECONDS) ? 1L : 1000L;
  }

  public SQLiteConfig.DateClass getDateClass() {
    return dateClass;
  }

  public void setDateClass(SQLiteConfig.DateClass dateClass) {
    this.dateClass = dateClass;
  }

  public SQLiteConfig.DatePrecision getDatePrecision() {
    return datePrecision;
  }

  public void setDatePrecision(SQLiteConfig.DatePrecision datePrecision) {
    this.datePrecision = datePrecision;
  }

  public String getDateStringFormat() {
    return dateStringFormat;
  }

  public void setDateStringFormat(String dateStringFormat) {
    this.dateStringFormat = dateStringFormat;
    dateFormat = FastDateFormat.getInstance(dateStringFormat);
  }

  public FastDateFormat getDateFormat() {
    return dateFormat;
  }

  public int getTransactionIsolation() {
    return transactionIsolation;
  }

  public void setTransactionIsolation(int transactionIsolation) {
    this.transactionIsolation = transactionIsolation;
  }

  public SQLiteConfig.TransactionMode getTransactionMode() {
    return transactionMode;
  }

  public void setTransactionMode(SQLiteConfig.TransactionMode transactionMode) {
    this.transactionMode = transactionMode;
  }

  public String transactionPrefix() {
    return beginCommandMap.get(transactionMode);
  }

  public static SQLiteConnectionConfig fromPragmaTable(Properties pragmaTable) {
    return new SQLiteConnectionConfig(
      SQLiteConfig.DateClass.getDateClass(
        pragmaTable.getProperty(
          SQLiteConfig.Pragma.DATE_CLASS.pragmaName,
          SQLiteConfig.DateClass.INTEGER.name())),
      SQLiteConfig.DatePrecision.getPrecision(
        pragmaTable.getProperty(
          SQLiteConfig.Pragma.DATE_PRECISION.pragmaName,
          SQLiteConfig.DatePrecision.MILLISECONDS.name())),
      pragmaTable.getProperty(
        SQLiteConfig.Pragma.DATE_STRING_FORMAT.pragmaName,
        SQLiteConfig.DEFAULT_DATE_STRING_FORMAT),
      Connection.TRANSACTION_SERIALIZABLE,
      SQLiteConfig.TransactionMode.getMode(
        pragmaTable.getProperty(
          SQLiteConfig.Pragma.TRANSACTION_MODE.pragmaName,
          SQLiteConfig.TransactionMode.DEFERRED.name()))
    );
  }
}
