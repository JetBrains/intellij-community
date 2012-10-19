/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConvertor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/18/12
 * Time: 5:30 PM
 */
public class SelectListsQueryHelper {
  private final CacheJdbcConnection myConnection;

  private final long myLast;
  private final long myFirst;
  private final Long myLocationId;
  private final String mySubfolder;
  private final boolean myNumberFake;

  public SelectListsQueryHelper(final CacheJdbcConnection connection, RevisionId existingLast, RevisionId existingFirst, RevisionId last,
                                RevisionId first, final Long locationId, String subfolder) {
    myConnection = connection;
    myNumberFake = last.isNumberFake() && first.isNumberFake() && ! (last.isFake() && first.isFake());
    myLast = operating(last, existingLast);
    myFirst = operating(first, existingFirst);
    myLocationId = locationId;
    mySubfolder = subfolder;
  }

  private long operating(RevisionId last, RevisionId existing) {
    assert ! existing.isFake();
    if (myNumberFake) {
      return last.isFake() ? existing.getTime() : last.getTime();
    }
    else {
      // we allow only one bound to be specified
      return last.isNumberFake() ? existing.getNumber() : last.getNumber();
    }
  }

  public PreparedStatement createStatement() throws VcsException, SQLException {
    if (myNumberFake) {
      // by dates
      if (StringUtil.isEmptyOrSpaces(mySubfolder)) {
        // no subfolder constraint
        return createDatesOnly();
      } else {
        return createDatesSubfolder();
      }
    } else {
      // by numbers
      if (StringUtil.isEmptyOrSpaces(mySubfolder)) {
        // no subfolder constraint
        return createNumbersOnly();
      } else {
        return createNumbersSubfolder();
      }
    }
  }

  private PreparedStatement createNumbersSubfolder() throws VcsException, SQLException {
    final PreparedStatement impl =
      createImpl(SqliteTables.PREPARED_NUMBERS_SUBFOLDER, " R." + SqliteTables.REVISION.NUMBER_INT + ">=? AND R." + SqliteTables.REVISION.NUMBER_INT + "<=? AND P." +
                 SqliteTables.PATHS.PATH + " LIKE ?");
    impl.setLong(2, myFirst);
    impl.setLong(3, myLast);
    impl.setString(4, mySubfolder + "%");
    return impl;
  }

  private PreparedStatement createNumbersOnly() throws VcsException, SQLException {
    final PreparedStatement impl =
      createImpl(SqliteTables.PREPARED_NUMBERS_ONLY, " R." + SqliteTables.REVISION.NUMBER_INT + ">=? AND R." + SqliteTables.REVISION.NUMBER_INT + "<=? ");
    impl.setLong(2, myFirst);
    impl.setLong(3, myLast);
    return impl;
  }

  private PreparedStatement createDatesSubfolder() throws VcsException, SQLException {
    final PreparedStatement impl =
      createImpl(SqliteTables.PREPARED_DATES_SUBFOLDER, " R." + SqliteTables.REVISION.DATE + ">=? AND R." + SqliteTables.REVISION.DATE + "<=? AND P." +
                 SqliteTables.PATHS.PATH + " LIKE ?");
    impl.setLong(2, myFirst);
    impl.setLong(3, myLast);
    impl.setString(4, mySubfolder + "%");
    return impl;
  }

  private PreparedStatement createDatesOnly() throws VcsException, SQLException {
    final PreparedStatement impl = createImpl(SqliteTables.PREPARED_DATES_ONLY, " R." + SqliteTables.REVISION.DATE + ">=? AND R." + SqliteTables.REVISION.DATE + "<=? ");
    impl.setLong(2, myFirst);
    impl.setLong(3, myLast);
    return impl;
  }

  private PreparedStatement createImpl(final String queryName, final String whereClause) throws VcsException, SQLException {
    final PreparedStatement statement = myConnection.getOrCreatePreparedStatement(queryName,
                                                                                  new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
                                                                                    @Override
                                                                                    public PreparedStatement convert(Connection connection)
                                                                                      throws SQLException {
                                                                                      return connection.prepareStatement("SELECT " +
                                                                                                                         SqliteTables.REVISION.RAW_DATA +
                                                                                                                         " , " + SqliteTables.REVISION.NUMBER_INT +
                                                                                                                         " FROM " +
                                                                                                                         SqliteTables.REVISION.TABLE_NAME +
                                                                                                                         " R INNER JOIN " +
                                                                                                                         SqliteTables.PATHS_2_REVS.TABLE_NAME +
                                                                                                                         " PR ON R." +
                                                                                                                         SqliteTables.REVISION.ID +
                                                                                                                         " = PR." +
                                                                                                                         SqliteTables.PATHS_2_REVS.REVISION_FK +
                                                                                                                         " , " +
                                                                                                                         SqliteTables.PATHS.TABLE_NAME +
                                                                                                                         " P ON PR." +
                                                                                                                         SqliteTables.PATHS_2_REVS.PATH_FK +
                                                                                                                         " = P." +
                                                                                                                         SqliteTables.PATHS.ID +
                                                                                                                         " WHERE R." +
                                                                                                                         SqliteTables.REVISION.ROOT_FK +
                                                                                                                         " = ? AND "
                                                                                                                         +
                                                                                                                         whereClause +
                                                                                                                         " ORDER BY " +
                                                                                                                         SqliteTables.REVISION.NUMBER_INT +
                                                                                                                         " DESC");
                                                                                    }
                                                                                  });
    statement.setLong(1, myLocationId);
    return statement;
  }
}
