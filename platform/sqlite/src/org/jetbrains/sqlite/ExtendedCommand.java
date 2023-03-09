// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// --------------------------------------
// sqlite-jdbc Project
//
// ExtendedCommand.java
// Since: Mar 12, 2010
//
// $URL$
// $Author$
// --------------------------------------
package org.jetbrains.sqlite;

import org.jetbrains.sqlite.core.DB;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * parsing SQLite specific extension of SQL command
 *
 * @author leo
 */
public final class ExtendedCommand {
  /**
   * Parses extended commands of "backup" or "restore" for SQLite database.
   *
   * @param sql One of the extended commands:<br>
   *            backup sourceDatabaseName to destinationFileName OR restore targetDatabaseName from
   *            sourceFileName
   * @return BackupCommand object if the argument is a backup command; RestoreCommand object if
   * the argument is a restore command;
   */
  public static SQLExtension parse(String sql) throws SQLException {
    if (sql == null) return null;
    if (sql.length() > 5 && sql.substring(0, 6).equalsIgnoreCase("backup")) {
      return BackupCommand.parse(sql);
    }
    else if (sql.length() > 6 && sql.substring(0, 7).equalsIgnoreCase("restore")) {
      return RestoreCommand.parse(sql);
    }

    return null;
  }

  /**
   * Remove the quotation mark from string.
   *
   * @param s String with quotation mark.
   * @return String with quotation mark removed.
   */
  public static String removeQuotation(String s) {
    if (s == null) return s;

    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    else {
      return s;
    }
  }

  public interface SQLExtension {
    void execute(DB db) throws SQLException;
  }

  public static class BackupCommand implements SQLExtension {
    private static final Pattern backupCmd =
      Pattern.compile(
        "backup(\\s+(\"[^\"]*\"|'[^']*'|\\S+))?\\s+to\\s+(\"[^\"]*\"|'[^']*'|\\S+)",
        Pattern.CASE_INSENSITIVE);
    public final String srcDB;
    public final String destFile;

    /**
     * Constructs a BackupCommand instance that backup the database to a target file.
     *
     * @param srcDB    Source database name.
     * @param destFile Target file name.
     */
    public BackupCommand(String srcDB, String destFile) {
      this.srcDB = srcDB;
      this.destFile = destFile;
    }

    @Override
    public void execute(DB db) throws SQLException {
      int rc = db.backup(srcDB, destFile, null);

      if (rc != SQLiteErrorCode.SQLITE_OK.code) {
        throw DB.newSQLException(rc, "Restore failed", null);
      }
    }

    /**
     * Parses SQLite database backup command and creates a BackupCommand object.
     *
     * @param sql SQLite database backup command.
     * @return BackupCommand object.
     */
    public static BackupCommand parse(String sql) throws SQLException {
      if (sql != null) {
        Matcher m = backupCmd.matcher(sql);
        if (m.matches()) {
          String dbName = removeQuotation(m.group(2));
          String dest = removeQuotation(m.group(3));
          if (dbName == null || dbName.length() == 0) dbName = "main";

          return new BackupCommand(dbName, dest);
        }
      }
      throw new SQLException("syntax error: " + sql);
    }
  }

  public static class RestoreCommand implements SQLExtension {
    private static final Pattern restoreCmd =
      Pattern.compile(
        "restore(\\s+(\"[^\"]*\"|'[^']*'|\\S+))?\\s+from\\s+(\"[^\"]*\"|'[^']*'|\\S+)",
        Pattern.CASE_INSENSITIVE);
    public final String targetDB;
    public final String srcFile;

    /**
     * Constructs a RestoreCommand instance that restores the database from a given source file.
     *
     * @param targetDB Target database name
     * @param srcFile  Source file name
     */
    public RestoreCommand(String targetDB, String srcFile) {
      this.targetDB = targetDB;
      this.srcFile = srcFile;
    }

    /** @see ExtendedCommand.SQLExtension#execute(DB) */
    @Override
    public void execute(DB db) throws SQLException {
      int rc = db.restore(targetDB, srcFile, null);

      if (rc != SQLiteErrorCode.SQLITE_OK.code) {
        throw DB.newSQLException(rc, "Restore failed", null);
      }
    }

    /**
     * Parses SQLite database restore command and creates a RestoreCommand object.
     *
     * @param sql SQLite restore backup command
     * @return RestoreCommand object.
     */
    public static RestoreCommand parse(String sql) throws SQLException {
      if (sql != null) {
        Matcher m = restoreCmd.matcher(sql);
        if (m.matches()) {
          String dbName = removeQuotation(m.group(2));
          String dest = removeQuotation(m.group(3));
          if (dbName == null || dbName.length() == 0) dbName = "main";
          return new RestoreCommand(dbName, dest);
        }
      }
      throw new SQLException("syntax error: " + sql);
    }
  }
}
