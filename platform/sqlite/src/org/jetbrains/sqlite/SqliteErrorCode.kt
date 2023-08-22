/*--------------------------------------------------------------------------
 *  Copyright 2009 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
// --------------------------------------
// sqlite-jdbc Project
//
// SQLiteErrorCode.java
// Since: Apr 21, 2009
//
// $URL$
// $Author$
// --------------------------------------
package org.jetbrains.sqlite

/**
 * SQLite3 error code
 *
 * @author leo
 * @see [http://www.sqlite.org/c3ref/c_abort.html](http://www.sqlite.org/c3ref/c_abort.html)
 */
enum class SqliteErrorCode(@JvmField val code: Int, @JvmField val message: String) {
  UNKNOWN_ERROR(-1, "unknown error"),
  SQLITE_OK(0, "Successful result"),

  /* beginning-of-error-codes */
  SQLITE_ERROR(1, "SQL error or missing database"),
  SQLITE_INTERNAL(2, "Internal logic error in SQLite"),
  SQLITE_PERM(3, "Access permission denied"),
  SQLITE_ABORT(4, "Callback routine requested an abort"),
  SQLITE_BUSY(5, "The database file is locked"),
  SQLITE_LOCKED(6, "A table in the database is locked"),
  SQLITE_NOMEM(7, "A malloc() failed"),
  SQLITE_READONLY(8, "Attempt to write a readonly database"),
  SQLITE_INTERRUPT(SqliteCodes.SQLITE_INTERRUPT, "Operation terminated by sqlite3_interrupt()"),
  SQLITE_IOERR(10, "Some kind of disk I/O error occurred"),
  SQLITE_CORRUPT(11, "The database disk image is malformed"),
  SQLITE_NOTFOUND(12, "NOT USED. Table or record not found"),
  SQLITE_FULL(13, "Insertion failed because database is full"),
  SQLITE_CANTOPEN(14, "Unable to open the database file"),
  SQLITE_PROTOCOL(15, "NOT USED. Database lock protocol error"),
  SQLITE_EMPTY(16, "Database is empty"),
  SQLITE_SCHEMA(17, "The database schema changed"),
  SQLITE_TOOBIG(18, "String or BLOB exceeds size limit"),
  SQLITE_CONSTRAINT(19, "Abort due to constraint violation"),
  SQLITE_MISMATCH(20, "Data type mismatch"),
  SQLITE_MISUSE(21, "Library used incorrectly"),
  SQLITE_NOLFS(22, "Uses OS features not supported on host"),
  SQLITE_AUTH(23, "Authorization denied"),
  SQLITE_FORMAT(24, "Auxiliary database format error"),
  SQLITE_RANGE(25, "2nd parameter to sqlite3_bind out of range"),
  SQLITE_NOTADB(26, "File opened that is not a database file"),
  SQLITE_NOTICE(27, "Notifications from sqlite3_log()"),
  SQLITE_WARNING(28, "Warnings from sqlite3_log()"),
  SQLITE_ROW(100, "sqlite3_step() has another row ready"),
  SQLITE_DONE(101, "sqlite3_step() has finished executing"),

  /* Beginning of extended error codes */
  SQLITE_ABORT_ROLLBACK(
    516,
    "The transaction that was active when the SQL statement first started was rolled back"),
  SQLITE_AUTH_USER(
    279,
    "An operation was attempted on a database for which the logged in user lacks sufficient authorization"),
  SQLITE_BUSY_RECOVERY(
    261, "Another process is busy recovering a WAL mode database file following a crash"),
  SQLITE_BUSY_SNAPSHOT(517, "Another database connection has already written to the database"),
  SQLITE_BUSY_TIMEOUT(
    773,
    "A blocking Posix advisory file lock request in the VFS layer failed due to a timeout"),
  SQLITE_CANTOPEN_CONVPATH(
    1038, "cygwin_conv_path() system call failed while trying to open a file"),
  SQLITE_CANTOPEN_DIRTYWAL(1294, "Not used"),
  SQLITE_CANTOPEN_FULLPATH(
    782, "The operating system was unable to convert the filename into a full pathname"),
  SQLITE_CANTOPEN_ISDIR(526, "The file is really a directory"),
  SQLITE_CANTOPEN_NOTEMPDIR(270, "No longer used"),
  SQLITE_CANTOPEN_SYMLINK(
    1550, "The file is a symbolic link but SQLITE_OPEN_NOFOLLOW flag is used"),
  SQLITE_CONSTRAINT_CHECK(275, "A CHECK constraint failed"),
  SQLITE_CONSTRAINT_COMMITHOOK(531, "A commit hook callback returned non-zero"),
  SQLITE_CONSTRAINT_DATATYPE(
    3091,
    "An insert or update attempted to store a value inconsistent with the column's declared type in a table defined as STRICT"),
  SQLITE_CONSTRAINT_FOREIGNKEY(787, "A foreign key constraint failed"),
  SQLITE_CONSTRAINT_FUNCTION(1043, "Error reported by extension function"),
  SQLITE_CONSTRAINT_NOTNULL(1299, "A NOT NULL constraint failed"),
  SQLITE_CONSTRAINT_PINNED(
    2835,
    "An UPDATE trigger attempted to delete the row that was being updated in the middle of the update"),
  SQLITE_CONSTRAINT_PRIMARYKEY(1555, "A PRIMARY KEY constraint failed"),
  SQLITE_CONSTRAINT_ROWID(2579, "rowid is not unique"),
  SQLITE_CONSTRAINT_TRIGGER(
    1811, "A RAISE function within a trigger fired, causing the SQL statement to abort"),
  SQLITE_CONSTRAINT_UNIQUE(2067, "A UNIQUE constraint failed"),
  SQLITE_CONSTRAINT_VTAB(2323, "Error reported by application-defined virtual table"),
  SQLITE_CORRUPT_INDEX(779, "SQLite detected an entry is or was missing from an index"),
  SQLITE_CORRUPT_SEQUENCE(523, "the schema of the sqlite_sequence table is corrupt"),
  SQLITE_CORRUPT_VTAB(267, "Content in the virtual table is corrupt"),
  SQLITE_ERROR_MISSING_COLLSEQ(
    257,
    "An SQL statement could not be prepared because a collating sequence named in that SQL statement could not be located"),
  SQLITE_ERROR_RETRY(513, "used internally"),
  SQLITE_ERROR_SNAPSHOT(769, "the historical snapshot is no longer available"),
  SQLITE_IOERR_ACCESS(3338, "I/O error within the xAccess"),
  SQLITE_IOERR_AUTH(7178, "reserved for use by extensions"),
  SQLITE_IOERR_BEGIN_ATOMIC(
    7434,
    "the underlying operating system reported and error on the SQLITE_FCNTL_BEGIN_ATOMIC_WRITE file-control"),
  SQLITE_IOERR_BLOCKED(2826, "no longer used"),
  SQLITE_IOERR_CHECKRESERVEDLOCK(3594, "I/O error within xCheckReservedLock"),
  SQLITE_IOERR_CLOSE(4106, "I/O error within xClose"),
  SQLITE_IOERR_COMMIT_ATOMIC(
    7690,
    "the underlying operating system reported and error on the SQLITE_FCNTL_COMMIT_ATOMIC_WRITE file-control"),
  SQLITE_IOERR_CONVPATH(6666, "cygwin_conv_path() system call failed"),
  SQLITE_IOERR_CORRUPTFS(
    8458,
    "I/O error in the VFS layer, a seek or read failure was due to the request not falling within the file's boundary rather than an ordinary device failure"),
  SQLITE_IOERR_DATA(
    8202,
    "I/O error in the VFS shim, the checksum on a page of the database file is incorrect"),
  SQLITE_IOERR_DELETE(2570, "I/O error within xDelete"),
  SQLITE_IOERR_DELETE_NOENT(5898, "The file being deleted does not exist"),
  SQLITE_IOERR_DIR_CLOSE(4362, "no longer used"),
  SQLITE_IOERR_DIR_FSYNC(
    1290, "I/O error in the VFS layer while trying to invoke fsync() on a directory"),
  SQLITE_IOERR_FSTAT(1802, "I/O error in the VFS layer while trying to invoke fstat()"),
  SQLITE_IOERR_FSYNC(
    1034, "I/O error in the VFS layer while trying to flush previously written content"),
  SQLITE_IOERR_GETTEMPPATH(
    6410, "Unable to determine a suitable directory in which to place temporary files"),
  SQLITE_IOERR_LOCK(3850, "I/O error in the advisory file locking logic"),
  SQLITE_IOERR_MMAP(6154, "I/O error while trying to map or unmap part of the database file"),
  SQLITE_IOERR_NOMEM(3082, "Unable to allocate sufficient memory"),
  SQLITE_IOERR_RDLOCK(2314, "I/O error within xLock"),
  SQLITE_IOERR_READ(266, "I/O error in the VFS layer while trying to read from a file on disk"),
  SQLITE_IOERR_ROLLBACK_ATOMIC(
    7946,
    "the underlying operating system reported and error on the SQLITE_FCNTL_ROLLBACK_ATOMIC_WRITE file-control"),
  SQLITE_IOERR_SEEK(5642, "I/O error while trying to seek a file descriptor"),
  SQLITE_IOERR_SHMLOCK(5130, "no longer used"),
  SQLITE_IOERR_SHMMAP(
    5386, "I/O error within xShmMap while trying to map a shared memory segment"),
  SQLITE_IOERR_SHMOPEN(
    4618, "I/O error within xShmMap while trying to open a new shared memory segment"),
  SQLITE_IOERR_SHMSIZE(
    4874,
    "I/O error within xShmMap while trying to resize an existing shared memory segment"),
  SQLITE_IOERR_SHORT_READ(
    522, "The VFS layer was unable to obtain as many bytes as was requested"),
  SQLITE_IOERR_TRUNCATE(
    1546, "I/O error in the VFS layer while trying to truncate a file to a smaller size"),
  SQLITE_IOERR_UNLOCK(2058, "I/O error within xUnlock"),
  SQLITE_IOERR_VNODE(6922, "reserved for use by extensions"),
  SQLITE_IOERR_WRITE(778, "I/O error in the VFS layer while trying to write to a file on disk"),
  SQLITE_LOCKED_SHAREDCACHE(
    262, "Contention with a different database connection that shares the cache"),
  SQLITE_LOCKED_VTAB(518, "reserved for use by extensions"),
  SQLITE_NOTICE_RECOVER_ROLLBACK(539, "a hot journal is rolled back"),
  SQLITE_NOTICE_RECOVER_WAL(283, "a WAL mode database file is recovered"),
  SQLITE_OK_LOAD_PERMANENTLY(
    256,
    "the extension remains loaded into the process address space after the database connection closes"),
  SQLITE_READONLY_CANTINIT(
    1288, "the current process does not have write permission on the shared memory region"),
  SQLITE_READONLY_CANTLOCK(
    520, "The shared-memory file associated with that database is read-only"),
  SQLITE_READONLY_DBMOVED(1032, "The database file has been moved since it was opened"),
  SQLITE_READONLY_DIRECTORY(
    1544,
    "Process does not have permission to create a journal file in the same directory as the database and the creation of a journal file is a prerequisite for writing"),
  SQLITE_READONLY_RECOVERY(264, "The database file needs to be recovered"),
  SQLITE_READONLY_ROLLBACK(776, "Hot journal needs to be rolled back"),
  SQLITE_WARNING_AUTOINDEX(284, "automatic indexing is used");

  /** @see Enum.toString
   */
  override fun toString(): String = "[$name] $message"

  companion object {
    /**
     * @param errorCode Error code.
     * @return Error message.
     */
    internal fun getErrorCode(errorCode: Int): SqliteErrorCode {
      return values().firstOrNull { errorCode == it.code } ?: UNKNOWN_ERROR
    }
  }
}