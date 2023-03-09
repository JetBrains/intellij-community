/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.jetbrains.sqlite.core;

public interface Codes {
  /** Successful result */
  int SQLITE_OK = 0;

  /** SQL error or missing database */
  int SQLITE_ERROR = 1;

  /** An internal logic error in SQLite */
  int SQLITE_INTERNAL = 2;

  /** Access permission denied */
  int SQLITE_PERM = 3;

  /** Callback routine requested an abort */
  int SQLITE_ABORT = 4;

  /** The database file is locked */
  int SQLITE_BUSY = 5;

  /** A table in the database is locked */
  int SQLITE_LOCKED = 6;

  /** A malloc() failed */
  int SQLITE_NOMEM = 7;

  /** Attempt to write a readonly database */
  int SQLITE_READONLY = 8;

  /** Operation terminated by sqlite_interrupt() */
  int SQLITE_INTERRUPT = 9;

  /** Some kind of disk I/O error occurred */
  int SQLITE_IOERR = 10;

  /** The database disk image is malformed */
  int SQLITE_CORRUPT = 11;

  /** (Internal Only) Table or record not found */
  int SQLITE_NOTFOUND = 12;

  /** Insertion failed because database is full */
  int SQLITE_FULL = 13;

  /** Unable to open the database file */
  int SQLITE_CANTOPEN = 14;

  /** Database lock protocol error */
  int SQLITE_PROTOCOL = 15;

  /** (Internal Only) Database table is empty */
  int SQLITE_EMPTY = 16;

  /** The database schema changed */
  int SQLITE_SCHEMA = 17;

  /** Too much data for one row of a table */
  int SQLITE_TOOBIG = 18;

  /** Abort due to constraint violation */
  int SQLITE_CONSTRAINT = 19;

  /** Data type mismatch */
  int SQLITE_MISMATCH = 20;

  /** Library used incorrectly */
  int SQLITE_MISUSE = 21;

  /** Uses OS features not supported on host */
  int SQLITE_NOLFS = 22;

  /** Authorization denied */
  int SQLITE_AUTH = 23;

  /** sqlite_step() has another row ready */
  int SQLITE_ROW = 100;

  /** sqlite_step() has finished executing */
  int SQLITE_DONE = 101;

  // types returned by sqlite3_column_type()

  int SQLITE_INTEGER = 1;
  int SQLITE_FLOAT = 2;
  int SQLITE_TEXT = 3;
  int SQLITE_BLOB = 4;
  int SQLITE_NULL = 5;
}
