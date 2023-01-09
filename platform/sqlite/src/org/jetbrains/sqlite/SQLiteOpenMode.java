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
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
// --------------------------------------
// sqlite-jdbc Project
//
// SQLiteOpenMode.java
// Since: Dec 8, 2009
//
// $URL$
// $Author$
// --------------------------------------
package org.jetbrains.sqlite;

/**
 * Database file open modes of SQLite.
 *
 * <p>See also <a href="http://sqlite.org/c3ref/open.html">...</a>
 *
 * @author leo
 */
public enum SQLiteOpenMode {
  READONLY(0x00000001), /* Ok for int SQLITE3_open_v2() */
  READWRITE(0x00000002), /* Ok for int SQLITE3_open_v2() */
  CREATE(0x00000004), /* Ok for int SQLITE3_open_v2() */
  DELETEONCLOSE(0x00000008), /* VFS only */
  EXCLUSIVE(0x00000010), /* VFS only */
  OPEN_URI(0x00000040), /* Ok for sqlite3_open_v2() */
  OPEN_MEMORY(0x00000080), /* Ok for sqlite3_open_v2() */
  MAIN_DB(0x00000100), /* VFS only */
  TEMP_DB(0x00000200), /* VFS only */
  TRANSIENT_DB(0x00000400), /* VFS only */
  MAIN_JOURNAL(0x00000800), /* VFS only */
  TEMP_JOURNAL(0x00001000), /* VFS only */
  SUBJOURNAL(0x00002000), /* VFS only */
  MASTER_JOURNAL(0x00004000), /* VFS only */
  NOMUTEX(0x00008000), /* Ok for int SQLITE3_open_v2() */
  FULLMUTEX(0x00010000), /* Ok for int SQLITE3_open_v2() */
  SHAREDCACHE(0x00020000), /* Ok for int SQLITE3_open_v2() */
  PRIVATECACHE(0x00040000) /* Ok for sqlite3_open_v2() */;

  public final int flag;

  SQLiteOpenMode(int flag) {
    this.flag = flag;
  }
}
