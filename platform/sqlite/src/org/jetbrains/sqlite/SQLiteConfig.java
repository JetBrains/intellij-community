/**
 * Copyright 2009 Taro L. Saito
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 * --------------------------------------------------------------------------
 */
// --------------------------------------
// sqlite-jdbc Project
//
// SQLiteConfig.java
// Since: Dec 8, 2009
//
// $URL$
// $Author$
// --------------------------------------
package org.jetbrains.sqlite;

import org.jetbrains.sqlite.core.SqliteConnection;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.*;

/**
 * SQLite Configuration
 *
 * <p>See also <a href="http://www.sqlite.org/pragma.html">...</a>
 *
 * @author leo
 */
public class SQLiteConfig {
  /* Date storage class*/
  public static final String DEFAULT_DATE_STRING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
  static final Set<String> pragmaSet = new TreeSet<>();
  /* Default limits used by SQLite: https://www.sqlite.org/limits.html */
  private static final int DEFAULT_MAX_LENGTH = 1000000000;
  private static final int DEFAULT_MAX_COLUMN = 2000;
  private static final int DEFAULT_MAX_SQL_LENGTH = 1000000;
  private static final int DEFAULT_MAX_FUNCTION_ARG = 100;
  private static final int DEFAULT_MAX_ATTACHED = 10;
  private static final int DEFAULT_MAX_PAGE_COUNT = 1073741823;
  private static final String[] OnOff = new String[]{"true", "false"};

  static {
    for (SQLiteConfig.Pragma pragma : SQLiteConfig.Pragma.values()) {
      pragmaSet.add(pragma.pragmaName);
    }
  }

  private final Properties pragmaTable;
  private final int busyTimeout;
  private final SQLiteConnectionConfig defaultConnectionConfig;
  private int openModeFlag = 0x00;
  private boolean explicitReadOnly;

  /** Default constructor. */
  public SQLiteConfig() {
    this(new Properties());
  }

  /**
   * Creates an SQLite configuration object using values from the given property object.
   *
   * @param prop The properties to apply to the configuration.
   */
  public SQLiteConfig(Properties prop) {
    pragmaTable = prop;

    String openMode = pragmaTable.getProperty(Pragma.OPEN_MODE.pragmaName);
    if (openMode != null) {
      openModeFlag = Integer.parseInt(openMode);
    }
    else {
      // set the default open mode of SQLite3
      setOpenMode(SQLiteOpenMode.READWRITE);
      setOpenMode(SQLiteOpenMode.CREATE);
    }
    // Shared Cache
    setSharedCache(
      Boolean.parseBoolean(
        pragmaTable.getProperty(Pragma.SHARED_CACHE.pragmaName, "false")));
    // Enable URI filenames
    setOpenMode(SQLiteOpenMode.OPEN_URI);

    busyTimeout =
      Integer.parseInt(pragmaTable.getProperty(Pragma.BUSY_TIMEOUT.pragmaName, "3000"));
    defaultConnectionConfig = SQLiteConnectionConfig.fromPragmaTable(pragmaTable);
    explicitReadOnly =
      Boolean.parseBoolean(
        pragmaTable.getProperty(Pragma.JDBC_EXPLICIT_READONLY.pragmaName, "false"));
  }

  public SQLiteConnectionConfig newConnectionConfig() {
    return defaultConnectionConfig.copyConfig();
  }

  /**
   * Configures a connection.
   *
   * @param conn The connection to configure.
   */
  public void apply(SqliteConnection conn) throws SQLException {

    HashSet<String> pragmaParams = new HashSet<>();
    for (Pragma each : Pragma.values()) {
      pragmaParams.add(each.pragmaName);
    }

    conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_ATTACHED,
        parseLimitPragma(Pragma.LIMIT_ATTACHED, DEFAULT_MAX_ATTACHED));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_COLUMN,
        parseLimitPragma(Pragma.LIMIT_COLUMN, DEFAULT_MAX_COLUMN));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_COMPOUND_SELECT,
        parseLimitPragma(Pragma.LIMIT_COMPOUND_SELECT, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_EXPR_DEPTH,
        parseLimitPragma(Pragma.LIMIT_EXPR_DEPTH, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_FUNCTION_ARG,
        parseLimitPragma(Pragma.LIMIT_FUNCTION_ARG, DEFAULT_MAX_FUNCTION_ARG));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_LENGTH,
        parseLimitPragma(Pragma.LIMIT_LENGTH, DEFAULT_MAX_LENGTH));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_LIKE_PATTERN_LENGTH,
        parseLimitPragma(Pragma.LIMIT_LIKE_PATTERN_LENGTH, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_SQL_LENGTH,
        parseLimitPragma(Pragma.LIMIT_SQL_LENGTH, DEFAULT_MAX_SQL_LENGTH));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_TRIGGER_DEPTH,
        parseLimitPragma(Pragma.LIMIT_TRIGGER_DEPTH, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_VARIABLE_NUMBER,
        parseLimitPragma(Pragma.LIMIT_VARIABLE_NUMBER, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_VDBE_OP, parseLimitPragma(Pragma.LIMIT_VDBE_OP, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_WORKER_THREADS,
        parseLimitPragma(Pragma.LIMIT_WORKER_THREADS, -1));
      conn.setLimit(
        SQLiteLimits.SQLITE_LIMIT_PAGE_COUNT,
        parseLimitPragma(Pragma.LIMIT_PAGE_COUNT, DEFAULT_MAX_PAGE_COUNT));

    pragmaParams.remove(Pragma.OPEN_MODE.pragmaName);
    pragmaParams.remove(Pragma.SHARED_CACHE.pragmaName);
    pragmaParams.remove(Pragma.LOAD_EXTENSION.pragmaName);
    pragmaParams.remove(Pragma.DATE_PRECISION.pragmaName);
    pragmaParams.remove(Pragma.DATE_CLASS.pragmaName);
    pragmaParams.remove(Pragma.DATE_STRING_FORMAT.pragmaName);
    pragmaParams.remove(Pragma.PASSWORD.pragmaName);
    pragmaParams.remove(Pragma.HEXKEY_MODE.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_ATTACHED.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_COLUMN.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_COMPOUND_SELECT.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_EXPR_DEPTH.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_FUNCTION_ARG.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_LENGTH.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_LIKE_PATTERN_LENGTH.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_SQL_LENGTH.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_TRIGGER_DEPTH.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_VARIABLE_NUMBER.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_VDBE_OP.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_WORKER_THREADS.pragmaName);
    pragmaParams.remove(Pragma.LIMIT_PAGE_COUNT.pragmaName);

    // exclude this "fake" pragma from execution
    pragmaParams.remove(Pragma.JDBC_EXPLICIT_READONLY.pragmaName);

    try (var stat = conn.createStatement()) {
      if (pragmaTable.containsKey(Pragma.PASSWORD.pragmaName)) {
        String password = pragmaTable.getProperty(Pragma.PASSWORD.pragmaName);
        if (password != null && !password.isEmpty()) {
          String hexkeyMode = pragmaTable.getProperty(Pragma.HEXKEY_MODE.pragmaName);
          String passwordPragma;
          if (HexKeyMode.SSE.name().equalsIgnoreCase(hexkeyMode)) {
            passwordPragma = "pragma hexkey = '%s'";
          }
          else if (HexKeyMode.SQLCIPHER.name().equalsIgnoreCase(hexkeyMode)) {
            passwordPragma = "pragma key = \"x'%s'\"";
          }
          else {
            passwordPragma = "pragma key = '%s'";
          }
          stat.execute(String.format(passwordPragma, password.replace("'", "''")));
          stat.execute("select 1 from sqlite_master");
        }
      }

      for (Object each : pragmaTable.keySet()) {
        String key = each.toString();
        if (!pragmaParams.contains(key)) {
          continue;
        }

        String value = pragmaTable.getProperty(key);
        if (value != null) {
          stat.execute(String.format("pragma %s=%s", key, value));
        }
      }
    }
  }

  /**
   * Sets a pragma to the given boolean value.
   *
   * @param pragma The pragma to set.
   * @param flag   The boolean value.
   */
  private void set(Pragma pragma, boolean flag) {
    setPragma(pragma, Boolean.toString(flag));
  }

  /**
   * Sets a pragma to the given int value.
   *
   * @param pragma The pragma to set.
   * @param num    The int value.
   */
  private void set(Pragma pragma, int num) {
    setPragma(pragma, Integer.toString(num));
  }

  /**
   * Checks if the provided value is the default for a given pragma.
   *
   * @param pragma       The pragma on which to check.
   * @param defaultValue The value to check for.
   * @return True if the given value is the default value; false otherwise.
   */
  private boolean getBoolean(Pragma pragma, String defaultValue) {
    return Boolean.parseBoolean(pragmaTable.getProperty(pragma.pragmaName, defaultValue));
  }

  /**
   * Retrieves a pragma integer value.
   *
   * @param pragma       The pragma.
   * @param defaultValue The default value.
   * @return The value of the pragma or defaultValue.
   */
  private int parseLimitPragma(Pragma pragma, int defaultValue) {
    if (!pragmaTable.containsKey(pragma.pragmaName)) {
      return defaultValue;
    }
    String valueString = pragmaTable.getProperty(pragma.pragmaName);
    try {
      return Integer.parseInt(valueString);
    }
    catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  /**
   * Checks if the shared cache option is turned on.
   *
   * @return True if turned on; false otherwise.
   */
  public boolean isEnabledSharedCache() {
    return getBoolean(Pragma.SHARED_CACHE, "false");
  }

  /**
   * Checks if the load extension option is turned on.
   *
   * @return True if turned on; false otherwise.
   */
  public boolean isEnabledLoadExtension() {
    return getBoolean(Pragma.LOAD_EXTENSION, "false");
  }

  /** @return The open mode flags. */
  public int getOpenModeFlags() {
    return openModeFlag;
  }

  /**
   * Sets a pragma's value.
   *
   * @param pragma The pragma to change.
   * @param value  The value to set it to.
   */
  public void setPragma(Pragma pragma, String value) {
    pragmaTable.put(pragma.pragmaName, value);
  }

  /** @return true if explicit read only transactions are enabled */
  public boolean isExplicitReadOnly() {
    return explicitReadOnly;
  }

  /**
   * Enable read only transactions after connection creation if explicit read only is true.
   *
   * @param readOnly whether to enable explicit read only
   */
  public void setExplicitReadOnly(boolean readOnly) {
    explicitReadOnly = readOnly;
  }

  /**
   * Sets the open mode flags.
   *
   * @param mode The open mode.
   * @see <a
   * href="http://www.sqlite.org/c3ref/c_open_autoproxy.html">http://www.sqlite.org/c3ref/c_open_autoproxy.html</a>
   */
  public void setOpenMode(SQLiteOpenMode mode) {
    openModeFlag |= mode.flag;
  }

  /**
   * Re-sets the open mode flags.
   *
   * @param mode The open mode.
   * @see <a
   * href="http://www.sqlite.org/c3ref/c_open_autoproxy.html">http://www.sqlite.org/c3ref/c_open_autoproxy.html</a>
   */
  public void resetOpenMode(SQLiteOpenMode mode) {
    openModeFlag &= ~mode.flag;
  }

  /**
   * Enables or disables the sharing of the database cache and schema data structures between
   * connections to the same database.
   *
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/c3ref/enable_shared_cache.html">www.sqlite.org/c3ref/enable_shared_cache.html</a>
   */
  public void setSharedCache(boolean enable) {
    set(Pragma.SHARED_CACHE, enable);
  }

  /**
   * Enables or disables extension loading.
   *
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/c3ref/load_extension.html">www.sqlite.org/c3ref/load_extension.html</a>
   */
  public void enableLoadExtension(boolean enable) {
    set(Pragma.LOAD_EXTENSION, enable);
  }

  /**
   * Sets the read-write mode for the database.
   *
   * @param readOnly True for read-only; otherwise read-write.
   */
  public void setReadOnly(boolean readOnly) {
    if (readOnly) {
      setOpenMode(SQLiteOpenMode.READONLY);
      resetOpenMode(SQLiteOpenMode.CREATE);
      resetOpenMode(SQLiteOpenMode.READWRITE);
    }
    else {
      setOpenMode(SQLiteOpenMode.READWRITE);
      setOpenMode(SQLiteOpenMode.CREATE);
      resetOpenMode(SQLiteOpenMode.READONLY);
    }
  }

  /**
   * Changes the maximum number of database disk pages that SQLite will hold in memory at once per
   * open database file.
   *
   * @param numberOfPages Cache size in number of pages.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_cache_size">www.sqlite.org/pragma.html#pragma_cache_size</a>
   */
  public void setCacheSize(int numberOfPages) {
    set(Pragma.CACHE_SIZE, numberOfPages);
  }

  /**
   * Enables or disables case sensitive for the LIKE operator.
   *
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_case_sensitive_like">www.sqlite.org/pragma.html#pragma_case_sensitive_like</a>
   */
  public void enableCaseSensitiveLike(boolean enable) {
    set(Pragma.CASE_SENSITIVE_LIKE, enable);
  }

  /**
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_count_changes">www.sqlite.org/pragma.html#pragma_count_changes</a>
   * @deprecated Enables or disables the count-changes flag. When enabled, INSERT, UPDATE and
   * DELETE statements return the number of rows they modified.
   */
  @Deprecated
  public void enableCountChanges(boolean enable) {
    set(Pragma.COUNT_CHANGES, enable);
  }

  /**
   * Sets the suggested maximum number of database disk pages that SQLite will hold in memory at
   * once per open database file. The cache size set here persists across database connections.
   *
   * @param numberOfPages Cache size in number of pages.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_cache_size">www.sqlite.org/pragma.html#pragma_cache_size</a>
   */
  public void setDefaultCacheSize(int numberOfPages) {
    set(Pragma.DEFAULT_CACHE_SIZE, numberOfPages);
  }

  /**
   * Defers enforcement of foreign key constraints until the outermost transaction is committed.
   *
   * @param enable True to enable; false to disable;
   * @see <a
   * href="https://www.sqlite.org/pragma.html#pragma_defer_foreign_keys">https://www.sqlite.org/pragma.html#pragma_defer_foreign_keys</a>
   */
  public void deferForeignKeys(boolean enable) {
    set(Pragma.DEFER_FOREIGN_KEYS, enable);
  }

  /**
   * @param enable True to enable; false to disable. false.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_empty_result_callbacks">http://www.sqlite.org/pragma.html#pragma_empty_result_callbacks</a>
   * @deprecated Enables or disables the empty_result_callbacks flag.
   */
  @Deprecated
  public void enableEmptyResultCallBacks(boolean enable) {
    set(Pragma.EMPTY_RESULT_CALLBACKS, enable);
  }

  /**
   * Sets the text encoding used by the main database.
   *
   * @param encoding One of {@link Encoding}
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_encoding">www.sqlite.org/pragma.html#pragma_encoding</a>
   */
  public void setEncoding(Encoding encoding) {
    setPragma(Pragma.ENCODING, encoding.typeName);
  }

  /**
   * Whether to enforce foreign key constraints. This setting affects the execution of all
   * statements prepared using the database connection, including those prepared before the
   * setting was changed.
   *
   * @param enforce True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_foreign_keys">www.sqlite.org/pragma.html#pragma_foreign_keys</a>
   */
  public void enforceForeignKeys(boolean enforce) {
    set(Pragma.FOREIGN_KEYS, enforce);
  }

  /**
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_full_column_names">www.sqlite.org/pragma.html#pragma_full_column_names</a>
   * @deprecated Enables or disables the full_column_name flag. This flag together with the
   * short_column_names flag determine the way SQLite assigns names to result columns of
   * SELECT statements.
   */
  @Deprecated
  public void enableFullColumnNames(boolean enable) {
    set(Pragma.FULL_COLUMN_NAMES, enable);
  }

  /**
   * Enables or disables the fullfsync flag. This flag determines whether or not the F_FULLFSYNC
   * syncing method is used on systems that support it. The default value of the fullfsync flag is
   * off. Only Mac OS X supports F_FULLFSYNC.
   *
   * @param enable True to enable; false to disable.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_fullfsync">www.sqlite.org/pragma.html#pragma_fullfsync</a>
   */
  public void enableFullSync(boolean enable) {
    set(Pragma.FULL_SYNC, enable);
  }

  /**
   * Sets the incremental_vacuum value; the number of pages to be removed from the <a
   * href="http://www.sqlite.org/fileformat2.html#freelist">freelist</a>. The database file is
   * truncated by the same amount.
   *
   * @param numberOfPagesToBeRemoved The number of pages to be removed.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_incremental_vacuum">www.sqlite.org/pragma.html#pragma_incremental_vacuum</a>
   */
  public void incrementalVacuum(int numberOfPagesToBeRemoved) {
    set(Pragma.INCREMENTAL_VACUUM, numberOfPagesToBeRemoved);
  }

  /**
   * Sets the journal mode for databases associated with the current database connection.
   *
   * @param mode One of {@link JournalMode}
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_journal_mode">www.sqlite.org/pragma.html#pragma_journal_mode</a>
   */
  public void setJournalMode(JournalMode mode) {
    setPragma(Pragma.JOURNAL_MODE, mode.name());
  }

  /**
   * Sets the journal_size_limit. This setting limits the size of the rollback-journal and WAL
   * files left in the file-system after transactions or checkpoints.
   *
   * @param limit Limit value in bytes. A negative number implies no limit.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_journal_size_limit">www.sqlite.org/pragma.html#pragma_journal_size_limit</a>
   */
  public void setJounalSizeLimit(int limit) {
    set(Pragma.JOURNAL_SIZE_LIMIT, limit);
  }

  /**
   * Sets the value of the legacy_file_format flag. When this flag is enabled, new SQLite
   * databases are created in a file format that is readable and writable by all versions of
   * SQLite going back to 3.0.0. When the flag is off, new databases are created using the latest
   * file format which might not be readable or writable by versions of SQLite prior to 3.3.0.
   *
   * @param use True to turn on legacy file format; false to turn off.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_legacy_file_format">www.sqlite.org/pragma.html#pragma_legacy_file_format</a>
   */
  public void useLegacyFileFormat(boolean use) {
    set(Pragma.LEGACY_FILE_FORMAT, use);
  }

  /**
   * Sets the database connection locking-mode.
   *
   * @param mode One of {@link LockingMode}
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_locking_mode">www.sqlite.org/pragma.html#pragma_locking_mode</a>
   */
  public void setLockingMode(LockingMode mode) {
    setPragma(Pragma.LOCKING_MODE, mode.name());
  }

  /**
   * Sets the page size of the database. The page size must be a power of two between 512 and
   * 65536 inclusive.
   *
   * @param numBytes A power of two between 512 and 65536 inclusive.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_page_size">www.sqlite.org/pragma.html#pragma_page_size</a>
   */
  public void setPageSize(int numBytes) {
    set(Pragma.PAGE_SIZE, numBytes);
  }

  /**
   * Sets the maximum number of pages in the database file.
   *
   * @param numPages Number of pages.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_max_page_count">www.sqlite.org/pragma.html#pragma_max_page_count</a>
   */
  public void setMaxPageCount(int numPages) {
    set(Pragma.MAX_PAGE_COUNT, numPages);
  }

  /**
   * Enables or disables useReadUncommitedIsolationMode.
   *
   * @param useReadUncommitedIsolationMode True to turn on; false to disable. disabled otherwise.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_read_uncommitted">www.sqlite.org/pragma.html#pragma_read_uncommitted</a>
   */
  public void setReadUncommited(boolean useReadUncommitedIsolationMode) {
    set(Pragma.READ_UNCOMMITTED, useReadUncommitedIsolationMode);
  }

  /**
   * Enables or disables the recursive trigger capability.
   *
   * @param enable True to enable the recursive trigger capability.
   * @see <a
   * href="www.sqlite.org/pragma.html#pragma_recursive_triggers">www.sqlite.org/pragma.html#pragma_recursive_triggers</a>
   */
  public void enableRecursiveTriggers(boolean enable) {
    set(Pragma.RECURSIVE_TRIGGERS, enable);
  }

  /**
   * Enables or disables the reverse_unordered_selects flag. This setting causes SELECT statements
   * without an ORDER BY clause to emit their results in the reverse order of what they normally
   * would. This can help debug applications that are making invalid assumptions about the result
   * order.
   *
   * @param enable True to enable reverse_unordered_selects.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_reverse_unordered_selects">www.sqlite.org/pragma.html#pragma_reverse_unordered_selects</a>
   */
  public void enableReverseUnorderedSelects(boolean enable) {
    set(Pragma.REVERSE_UNORDERED_SELECTS, enable);
  }

  /**
   * Enables or disables the short_column_names flag. This flag affects the way SQLite names
   * columns of data returned by SELECT statements.
   *
   * @param enable True to enable short_column_names.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_short_column_names">www.sqlite.org/pragma.html#pragma_short_column_names</a>
   */
  public void enableShortColumnNames(boolean enable) {
    set(Pragma.SHORT_COLUMN_NAMES, enable);
  }

  /**
   * Changes the setting of the "synchronous" flag.
   *
   * @param mode One of {@link SynchronousMode}:
   *             <ul>
   *               <li>OFF - SQLite continues without syncing as soon as it has handed data off to the
   *                   operating system
   *               <li>NORMAL - the SQLite database engine will still sync at the most critical moments,
   *                   but less often than in FULL mode
   *               <li>FULL - the SQLite database engine will use the xSync method of the VFS to ensure
   *                   that all content is safely written to the disk surface prior to continuing. This
   *                   ensures that an operating system crash or power failure will not corrupt the
   *                   database.
   *             </ul>
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_synchronous">www.sqlite.org/pragma.html#pragma_synchronous</a>
   */
  public void setSynchronous(SynchronousMode mode) {
    setPragma(Pragma.SYNCHRONOUS, mode.name());
  }

  /**
   * Changes the setting of the "hexkey" flag.
   *
   * @param mode One of {@link HexKeyMode}:
   *             <ul>
   *               <li>NONE - SQLite uses a string based password
   *               <li>SSE - the SQLite database engine will use pragma hexkey = '' to set the password
   *               <li>SQLCIPHER - the SQLite database engine calls pragma key = "x''" to set the password
   *             </ul>
   */
  public void setHexKeyMode(HexKeyMode mode) {
    setPragma(Pragma.HEXKEY_MODE, mode.name());
  }

  /**
   * Changes the setting of the "temp_store" parameter.
   *
   * @param storeType One of {@link TempStore}:
   *                  <ul>
   *                    <li>DEFAULT - the compile-time C preprocessor macro SQLITE_TEMP_STORE is used to
   *                        determine where temporary tables and indices are stored
   *                    <li>FILE - temporary tables and indices are stored in a file.
   *                  </ul>
   *                  <li>MEMORY - temporary tables and indices are kept in as if they were pure in-memory
   *                      databases memory
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_temp_store">www.sqlite.org/pragma.html#pragma_temp_store</a>
   */
  public void setTempStore(TempStore storeType) {
    setPragma(Pragma.TEMP_STORE, storeType.name());
  }

  /**
   * Changes the value of the sqlite3_temp_directory global variable, which many operating-system
   * interface backends use to determine where to store temporary tables and indices.
   *
   * @param directoryName Directory name for storing temporary tables and indices.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_temp_store_directory">www.sqlite.org/pragma.html#pragma_temp_store_directory</a>
   */
  public void setTempStoreDirectory(String directoryName) {
    setPragma(Pragma.TEMP_STORE_DIRECTORY, String.format("'%s'", directoryName));
  }

  /**
   * Set the value of the user-version. The user-version is not used internally by SQLite. It may
   * be used by applications for any purpose. The value is stored in the database header at offset
   * 60.
   *
   * @param version A big-endian 32-bit signed integer.
   * @see <a
   * href="http://www.sqlite.org/pragma.html#pragma_user_version">www.sqlite.org/pragma.html#pragma_user_version</a>
   */
  public void setUserVersion(int version) {
    set(Pragma.USER_VERSION, version);
  }

  /**
   * Set the value of the application-id. The application-id is not used internally by SQLite.
   * Applications that use SQLite as their application file-format should set the Application ID
   * integer to a unique integer so that utilities such as file(1) can determine the specific file
   * type. The value is stored in the database header at offset 68.
   *
   * @param id A big-endian 32-bit unsigned integer.
   * @see <a
   * href="http://sqlite.org/pragma.html#pragma_application_id">www.sqlite.org/pragma.html#pragma_application_id</a>
   */
  public void setApplicationId(int id) {
    set(Pragma.APPLICATION_ID, id);
  }

  /** @return The transaction mode. */
  public TransactionMode getTransactionMode() {
    return defaultConnectionConfig.getTransactionMode();
  }

  /**
   * Sets the mode that will be used to start transactions.
   *
   * @param transactionMode One of {@link TransactionMode}.
   * @see <a
   * href="http://www.sqlite.org/lang_transaction.html">http://www.sqlite.org/lang_transaction.html</a>
   */
  public void setTransactionMode(TransactionMode transactionMode) {
    defaultConnectionConfig.setTransactionMode(transactionMode);
  }

  /**
   * Sets the mode that will be used to start transactions.
   *
   * @param transactionMode One of DEFERRED, IMMEDIATE or EXCLUSIVE.
   * @see <a
   * href="http://www.sqlite.org/lang_transaction.html">http://www.sqlite.org/lang_transaction.html</a>
   */
  public void setTransactionMode(String transactionMode) {
    setTransactionMode(TransactionMode.getMode(transactionMode));
  }

  /** @param datePrecision One of SECONDS or MILLISECONDS */
  public void setDatePrecision(String datePrecision) {
    defaultConnectionConfig.setDatePrecision(DatePrecision.getPrecision(datePrecision));
  }

  /** @param dateClass One of INTEGER, TEXT or REAL */
  public void setDateClass(String dateClass) {
    defaultConnectionConfig.setDateClass(DateClass.getDateClass(dateClass));
  }

  /** @param dateStringFormat Format of date string */
  public void setDateStringFormat(String dateStringFormat) {

    defaultConnectionConfig.setDateStringFormat(dateStringFormat);
  }

  public int getBusyTimeout() {
    return busyTimeout;
  }

  /** @param milliseconds Connect to DB timeout in milliseconds */
  public void setBusyTimeout(int milliseconds) {
    setPragma(Pragma.BUSY_TIMEOUT, Integer.toString(milliseconds));
  }

  /** @return Array of DriverPropertyInfo objects. */
  static DriverPropertyInfo[] getDriverPropertyInfo() {
    Pragma[] pragma = Pragma.values();
    DriverPropertyInfo[] result = new DriverPropertyInfo[pragma.length];
    int index = 0;
    for (Pragma p : Pragma.values()) {
      DriverPropertyInfo di = new DriverPropertyInfo(p.pragmaName, null);
      di.choices = p.choices;
      di.description = p.description;
      di.required = false;
      result[index++] = di;
    }

    return result;
  }

  /**
   * Convert the given enum values to a string array
   *
   * @param list Array if PragmaValue.
   * @return String array of Enum values
   */
  private static String[] toStringArray(PragmaValue[] list) {
    String[] result = new String[list.length];
    for (int i = 0; i < list.length; i++) {
      result[i] = list[i].getValue();
    }
    return result;
  }

  public enum Pragma {

    // Parameters requiring SQLite3 API invocation
    OPEN_MODE("open_mode", "Database open-mode flag", null),
    SHARED_CACHE("shared_cache", "Enable SQLite Shared-Cache mode, native driver only", OnOff),
    LOAD_EXTENSION(
      "enable_load_extension",
      "Enable SQLite load_extension() function, native driver only",
      OnOff),

    // Pragmas that can be set after opening the database
    CACHE_SIZE(
      "cache_size",
      "Maximum number of database disk pages that SQLite will hold in memory at once per open database file",
      null),
    MMAP_SIZE(
      "mmap_size",
      "Maximum number of bytes that are set aside for memory-mapped I/O on a single database",
      null),
    CASE_SENSITIVE_LIKE(
      "case_sensitive_like",
      "Installs a new application-defined LIKE function that is either case sensitive or insensitive depending on the value",
      OnOff),
    COUNT_CHANGES("count_changes", "Deprecated", OnOff),
    DEFAULT_CACHE_SIZE("default_cache_size", "Deprecated", null),
    DEFER_FOREIGN_KEYS(
      "defer_foreign_keys",
      "When the defer_foreign_keys PRAGMA is on, enforcement of all foreign key constraints is delayed until the outermost transaction is committed. The defer_foreign_keys pragma defaults to OFF so that foreign key constraints are only deferred if they are created as \"DEFERRABLE INITIALLY DEFERRED\". The defer_foreign_keys pragma is automatically switched off at each COMMIT or ROLLBACK. Hence, the defer_foreign_keys pragma must be separately enabled for each transaction. This pragma is only meaningful if foreign key constraints are enabled, of course.",
      OnOff),
    EMPTY_RESULT_CALLBACKS("empty_result_callback", "Deprecated", OnOff),
    ENCODING(
      "encoding",
      "Set the encoding that the main database will be created with if it is created by this session",
      toStringArray(Encoding.values())),
    FOREIGN_KEYS("foreign_keys", "Set the enforcement of foreign key constraints", OnOff),
    FULL_COLUMN_NAMES("full_column_names", "Deprecated", OnOff),
    FULL_SYNC(
      "fullsync",
      "Whether or not the F_FULLFSYNC syncing method is used on systems that support it. Only Mac OS X supports F_FULLFSYNC.",
      OnOff),
    INCREMENTAL_VACUUM(
      "incremental_vacuum",
      "Causes up to N pages to be removed from the freelist. The database file is truncated by the same amount. The incremental_vacuum pragma has no effect if the database is not in auto_vacuum=incremental mode or if there are no pages on the freelist. If there are fewer than N pages on the freelist, or if N is less than 1, or if the \"(N)\" argument is omitted, then the entire freelist is cleared.",
      null),
    JOURNAL_MODE(
      "journal_mode",
      "Set the journal mode for databases associated with the current database connection",
      toStringArray(JournalMode.values())),
    JOURNAL_SIZE_LIMIT(
      "journal_size_limit",
      "Limit the size of rollback-journal and WAL files left in the file-system after transactions or checkpoints",
      null),
    LEGACY_FILE_FORMAT("legacy_file_format", "No-op", OnOff),
    LOCKING_MODE(
      "locking_mode",
      "Set the database connection locking-mode",
      toStringArray(LockingMode.values())),
    PAGE_SIZE(
      "page_size",
      "Set the page size of the database. The page size must be a power of two between 512 and 65536 inclusive.",
      null),
    MAX_PAGE_COUNT(
      "max_page_count", "Set the maximum number of pages in the database file", null),
    READ_UNCOMMITTED("read_uncommitted", "Set READ UNCOMMITTED isolation", OnOff),
    RECURSIVE_TRIGGERS("recursive_triggers", "Set the recursive trigger capability", OnOff),
    REVERSE_UNORDERED_SELECTS(
      "reverse_unordered_selects",
      "When enabled, this PRAGMA causes many SELECT statements without an ORDER BY clause to emit their results in the reverse order from what they normally would",
      OnOff),
    SECURE_DELETE(
      "secure_delete",
      "When secure_delete is on, SQLite overwrites deleted content with zeros",
      new String[]{"true", "false", "fast"}),
    SHORT_COLUMN_NAMES("short_column_names", "Deprecated", OnOff),
    SYNCHRONOUS(
      "synchronous",
      "Set the \"synchronous\" flag",
      toStringArray(SynchronousMode.values())),
    TEMP_STORE(
      "temp_store",
      "When temp_store is DEFAULT (0), the compile-time C preprocessor macro SQLITE_TEMP_STORE is used to determine where temporary tables and indices are stored. When temp_store is MEMORY (2) temporary tables and indices are kept as if they were in pure in-memory databases. When temp_store is FILE (1) temporary tables and indices are stored in a file. The temp_store_directory pragma can be used to specify the directory containing temporary files when FILE is specified. When the temp_store setting is changed, all existing temporary tables, indices, triggers, and views are immediately deleted.",
      toStringArray(TempStore.values())),
    TEMP_STORE_DIRECTORY("temp_store_directory", "Deprecated", null),
    USER_VERSION(
      "user_version",
      "Set the value of the user-version integer at offset 60 in the database header. The user-version is an integer that is available to applications to use however they want. SQLite makes no use of the user-version itself.",
      null),
    APPLICATION_ID(
      "application_id",
      "Set the 32-bit signed big-endian \"Application ID\" integer located at offset 68 into the database header. Applications that use SQLite as their application file-format should set the Application ID integer to a unique integer so that utilities such as file(1) can determine the specific file type rather than just reporting \"SQLite3 Database\"",
      null),

    // Limits
    LIMIT_LENGTH(
      "limit_length",
      "The maximum size of any string or BLOB or table row, in bytes.",
      null),
    LIMIT_SQL_LENGTH(
      "limit_sql_length", "The maximum length of an SQL statement, in bytes.", null),
    LIMIT_COLUMN(
      "limit_column",
      "The maximum number of columns in a table definition or in the result set of a SELECT or the maximum number of columns in an index or in an ORDER BY or GROUP BY clause.",
      null),
    LIMIT_EXPR_DEPTH(
      "limit_expr_depth", "The maximum depth of the parse tree on any expression.", null),
    LIMIT_COMPOUND_SELECT(
      "limit_compound_select",
      "The maximum number of terms in a compound SELECT statement.",
      null),
    LIMIT_VDBE_OP(
      "limit_vdbe_op",
      "The maximum number of instructions in a virtual machine program used to implement an SQL statement. If sqlite3_prepare_v2() or the equivalent tries to allocate space for more than this many opcodes in a single prepared statement, an SQLITE_NOMEM error is returned.",
      null),
    LIMIT_FUNCTION_ARG(
      "limit_function_arg", "The maximum number of arguments on a function.", null),
    LIMIT_ATTACHED("limit_attached", "The maximum number of attached databases.", null),
    LIMIT_LIKE_PATTERN_LENGTH(
      "limit_like_pattern_length",
      "The maximum length of the pattern argument to the LIKE or GLOB operators.",
      null),
    LIMIT_VARIABLE_NUMBER(
      "limit_variable_number",
      "The maximum index number of any parameter in an SQL statement.",
      null),
    LIMIT_TRIGGER_DEPTH(
      "limit_trigger_depth", "The maximum depth of recursion for triggers.", null),
    LIMIT_WORKER_THREADS(
      "limit_worker_threads",
      "The maximum number of auxiliary worker threads that a single prepared statement may start.",
      null),
    LIMIT_PAGE_COUNT(
      "limit_page_count",
      "The maximum number of pages allowed in a single database file.",
      null),

    // Others
    TRANSACTION_MODE(
      "transaction_mode",
      "Set the transaction mode",
      toStringArray(TransactionMode.values())),
    DATE_PRECISION(
      "date_precision",
      "\"seconds\": Read and store integer dates as seconds from the Unix Epoch (SQLite standard).\n\"milliseconds\": (DEFAULT) Read and store integer dates as milliseconds from the Unix Epoch (Java standard).",
      toStringArray(DatePrecision.values())),
    DATE_CLASS(
      "date_class",
      "\"integer\": (Default) store dates as number of seconds or milliseconds from the Unix Epoch\n\"text\": store dates as a string of text\n\"real\": store dates as Julian Dates",
      toStringArray(DateClass.values())),
    DATE_STRING_FORMAT(
      "date_string_format",
      "Format to store and retrieve dates stored as text. Defaults to \"yyyy-MM-dd HH:mm:ss.SSS\"",
      null),
    BUSY_TIMEOUT(
      "busy_timeout",
      "Sets a busy handler that sleeps for a specified amount of time when a table is locked",
      null),
    HEXKEY_MODE("hexkey_mode", "Mode of the secret key", toStringArray(HexKeyMode.values())),
    PASSWORD("password", "Database password", null),

    // extensions: "fake" pragmas to allow conformance with JDBC
    JDBC_EXPLICIT_READONLY(
      "jdbc.explicit_readonly", "Set explicit read only transactions", null);

    public final String pragmaName;
    public final String[] choices;
    public final String description;

    Pragma(String pragmaName) {
      this(pragmaName, null);
    }

    Pragma(String pragmaName, String[] choices) {
      this(pragmaName, null, choices);
    }

    Pragma(String pragmaName, String description, String[] choices) {
      this.pragmaName = pragmaName;
      this.description = description;
      this.choices = choices;
    }

    public final String getPragmaName() {
      return pragmaName;
    }
  }

  public enum Encoding implements PragmaValue {
    UTF8("'UTF-8'"),
    UTF16("'UTF-16'"),
    UTF16_LITTLE_ENDIAN("'UTF-16le'"),
    UTF16_BIG_ENDIAN("'UTF-16be'"),
    UTF_8(UTF8), // UTF-8
    UTF_16(UTF16), // UTF-16
    UTF_16LE(UTF16_LITTLE_ENDIAN), // UTF-16le
    UTF_16BE(UTF16_BIG_ENDIAN); // UTF-16be

    public final String typeName;

    Encoding(String typeName) {
      this.typeName = typeName;
    }

    Encoding(Encoding encoding) {
      typeName = encoding.getValue();
    }

    @Override
    public String getValue() {
      return typeName;
    }

    public static Encoding getEncoding(String value) {
      return valueOf(value.replaceAll("-", "_").toUpperCase());
    }
  }

  public enum JournalMode implements PragmaValue {
    DELETE,
    TRUNCATE,
    PERSIST,
    MEMORY,
    WAL,
    OFF;

    @Override
    public String getValue() {
      return name();
    }
  }

  public enum LockingMode implements PragmaValue {
    NORMAL,
    EXCLUSIVE;

    @Override
    public String getValue() {
      return name();
    }
  }

  public enum SynchronousMode implements PragmaValue {
    OFF,
    NORMAL,
    FULL;

    @Override
    public String getValue() {
      return name();
    }
  }

  public enum TempStore implements PragmaValue {
    DEFAULT,
    FILE,
    MEMORY;

    @Override
    public String getValue() {
      return name();
    }
  }

  public enum HexKeyMode implements PragmaValue {
    NONE,
    SSE,
    SQLCIPHER;

    @Override
    public String getValue() {
      return name();
    }
  }

  public enum TransactionMode implements PragmaValue {
    DEFERRED,
    IMMEDIATE,
    EXCLUSIVE;

    @Override
    public String getValue() {
      return name();
    }

    public static TransactionMode getMode(String mode) {
      if ("DEFFERED".equalsIgnoreCase(mode)) {
        return DEFERRED;
      }
      return valueOf(mode.toUpperCase(Locale.ENGLISH));
    }
  }

  public enum DatePrecision implements PragmaValue {
    SECONDS,
    MILLISECONDS;

    @Override
    public String getValue() {
      return name();
    }

    public static DatePrecision getPrecision(String precision) {
      return valueOf(precision.toUpperCase(Locale.ENGLISH));
    }
  }

  public enum DateClass implements PragmaValue {
    INTEGER,
    TEXT,
    REAL;

    @Override
    public String getValue() {
      return name();
    }

    public static DateClass getDateClass(String dateClass) {
      return valueOf(dateClass.toUpperCase(Locale.ENGLISH));
    }
  }

  /**
   * The common interface for retrieving the available pragma parameter values.
   *
   * @author leo
   */
  private interface PragmaValue {
    String getValue();
  }
}
