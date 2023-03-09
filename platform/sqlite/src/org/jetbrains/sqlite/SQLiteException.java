/*--------------------------------------------------------------------------
 *  Copyright 2016 Magnus Reftel
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
// SQLiteException.java
// Since: Jun 28, 2016
//
// $URL$
// $Author$
// --------------------------------------
package org.jetbrains.sqlite;

import java.sql.SQLException;

public final class SQLiteException extends SQLException {
  private final SQLiteErrorCode resultCode;

  public SQLiteException(String message, SQLiteErrorCode resultCode) {
    super(message, null, resultCode.code & 0xff);
    this.resultCode = resultCode;
  }

  public SQLiteErrorCode getResultCode() {
    return resultCode;
  }
}
