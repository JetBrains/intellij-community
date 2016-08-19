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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.ReceivedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/8/12
 * Time: 3:05 PM
 */
public class VcsSqliteLayer {
  private final static int ourLastPathRevisionBatchSize = 10;
  private final KnownRepositoryLocations myKnownRepositoryLocations;
  private final CacheJdbcConnection myConnection;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.dbCommitted.VcsSqliteLayer");

  public VcsSqliteLayer(final Project project, KnownRepositoryLocations locations) {
    myKnownRepositoryLocations = locations;
    myConnection = new CacheJdbcConnection(DbSettings.getDbFilePath(project),
                                           new ThrowableConsumer<Connection, VcsException>() {
                                             @Override
                                             public void consume(Connection connection) throws VcsException {
                                               initDb(connection);
                                             }
                                           });
  }

  private void initDb(Connection connection) throws VcsException {
    try {
      connection.createStatement().execute(createStatementForTable(SqliteTables.KNOWN_VCS));
      connection.createStatement().execute(createStatementForTable(SqliteTables.ROOT));
      connection.createStatement().execute(createStatementForTable(SqliteTables.AUTHOR));
      connection.createStatement().execute(createStatementForTable(SqliteTables.REVISION));
      connection.createStatement().execute(createStatementForTable(SqliteTables.PATHS));
      connection.createStatement().execute(createStatementForTable(SqliteTables.PATHS_2_REVS));
      connection.createStatement().execute(createStatementForTable(SqliteTables.INCOMING_PATHS));

      connection.createStatement().execute("CREATE INDEX " + SqliteTables.IDX_ROOT_URL + " ON ROOT (URL)");
      connection.createStatement().execute("CREATE INDEX " + "VCS_FK" + " ON ROOT (VCS_FK)");
      connection.createStatement().execute("CREATE INDEX " + SqliteTables.IDX_AUTHOR_NAME + " ON AUTHOR (NAME)");
      connection.createStatement().execute("CREATE INDEX " + SqliteTables.IDX_REVISION_DATE + " ON REVISION (DATE)");
      connection.createStatement().execute("CREATE INDEX " + SqliteTables.IDX_REVISION_NUMBER_INT + " ON REVISION (NUMBER_INT)");
      connection.createStatement().execute("CREATE INDEX " + SqliteTables.IDX_PATHS_PATH + " ON PATHS (PATH)");
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  public void checkVcsRootsAreTracked(final MultiMap<String, String> vcses) throws VcsException {
    if (vcses.isEmpty()) return;

    final MultiMap<String, String> copy = new MultiMap<>();
    copy.putAllValues(vcses);
    if (! checkForInMemory(copy)) return;

    final HashSet<String> vcsNamesSet = new HashSet<>(vcses.keySet());
    for (Iterator<String> iterator = vcsNamesSet.iterator(); iterator.hasNext(); ) {
      final String key = iterator.next();
      if (myKnownRepositoryLocations.exists(key)) {
        iterator.remove();
      }
    }

    if (! vcsNamesSet.isEmpty()) {
      ensureVcsAreInDB(vcsNamesSet);
    }

    final Set<Long> rootIdsToCheck = ensurePathsAreInDB(copy);
    if (! rootIdsToCheck.isEmpty()) {
      updateLastRevisions(rootIdsToCheck);
    }
  }

  private void updateLastRevisions(Set<Long> rootIdsToCheck) throws VcsException {
    final PreparedStatement maxStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_MAX_REVISION,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          final String num = SqliteTables.REVISION.NUMBER_INT;
          return connection.prepareStatement(
            "SELECT " + num + "MAX_REV, " + SqliteTables.REVISION.DATE + "MAX_DATE FROM " + SqliteTables.REVISION.TABLE_NAME +
            " WHERE MAX_REV=(" + " SELECT MAX(" + num + ")  FROM " + SqliteTables.REVISION.TABLE_NAME +
            " WHERE " + SqliteTables.REVISION.ROOT_FK + " =?");
        }
      });
    final PreparedStatement minStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_MIN_REVISION,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          final String num = SqliteTables.REVISION.NUMBER_INT;
          return connection.prepareStatement(
            "SELECT " + num + "MIN_REV, " + SqliteTables.REVISION.DATE + "MIN_DATE FROM " + SqliteTables.REVISION.TABLE_NAME +
            " WHERE MIN_REV=(" + " SELECT MIN(" + num + ")  FROM " + SqliteTables.REVISION.TABLE_NAME +
            " WHERE " + SqliteTables.REVISION.ROOT_FK + " =?");
        }
      });

    try {
      for (final Long id : rootIdsToCheck) {
        maxStatement.setLong(1, id);
        final ResultSet set = maxStatement.executeQuery();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long max = set.getLong(1);
            final long time = set.getLong(2);
            if (max > 0) {// 0 is === SQL NULL
              myKnownRepositoryLocations.setLastRevision(id, new RevisionId(max, time));
            }
          }
        });

        minStatement.setLong(1, id);
        final ResultSet setMin = minStatement.executeQuery();
        SqliteUtil.readSelectResults(setMin, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long min = setMin.getLong(1);
            final long time = setMin.getLong(2);
            if (min > 0) {// 0 is === SQL NULL
              myKnownRepositoryLocations.setFirstRevision(id, new RevisionId(min, time));
            }
          }
        });
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  private Set<Long> ensurePathsAreInDB(final MultiMap<String, String> copy) throws VcsException {
    final Set<Long> idsToCheck = new HashSet<>();
    final PreparedStatement select = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_ROOTS,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT * FROM " + SqliteTables.ROOT.TABLE_NAME + " WHERE VCS_FK=?");
        }
      });

    try {
      for (final String vcsName : copy.keySet()) {
        select.setLong(1, myKnownRepositoryLocations.getVcsKey(vcsName));
        final ResultSet set = select.executeQuery();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long id = set.getLong(SqliteTables.ROOT.ID);
            final String url = set.getString(SqliteTables.ROOT.URL);
            myKnownRepositoryLocations.add(vcsName, url, id);
            copy.remove(vcsName, url);
            if (myKnownRepositoryLocations.getLastRevision(id) == null) {
              idsToCheck.add(id);
            }
          }
        });
      }

      if (copy.isEmpty()) return idsToCheck;

      final PreparedStatement insert = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_ROOT,
        new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
          @Override
          public PreparedStatement convert(Connection connection) throws SQLException {
            return connection.prepareStatement("INSERT INTO " + SqliteTables.ROOT.TABLE_NAME + " ( " +
                                               SqliteTables.ROOT.VCS_FK + ", " + SqliteTables.ROOT.URL + ") VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
          }
        });
      for (String vcsName : copy.keySet()) {
        insert.setLong(1, myKnownRepositoryLocations.getVcsKey(vcsName));
        for (String path : copy.get(vcsName)) {
          insert.setString(2, path);
          final long id = SqliteUtil.insert(insert);
          myKnownRepositoryLocations.add(vcsName, path, id);
        }
      }
      myConnection.commit();
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
    return idsToCheck;
  }

  private void ensureVcsAreInDB(final HashSet<String> vcsNamesSet) throws VcsException {
    final PreparedStatement readVcses = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_VCS,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection o) throws SQLException {
          return o.prepareStatement("SELECT * FROM VCS");
        }
      });
    try {
      final ResultSet set = readVcses.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          final long id = set.getLong(SqliteTables.KNOWN_VCS.ID);
          final String name = set.getString(SqliteTables.KNOWN_VCS.NAME);
          myKnownRepositoryLocations.addVcs(name, id);
          vcsNamesSet.remove(name);
        }
      });

      if (vcsNamesSet.isEmpty()) return;

      final PreparedStatement insertStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_VCS,
        new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
          @Override
          public PreparedStatement convert(Connection o) throws SQLException {
            return o.prepareStatement("INSERT INTO " + SqliteTables.KNOWN_VCS.TABLE_NAME + " (" + SqliteTables.KNOWN_VCS.NAME +
                                      ") VALUES (?)", Statement.RETURN_GENERATED_KEYS);
          }
        });
      for (String name : vcsNamesSet) {
        insertStatement.setString(1, name);
        final long id = SqliteUtil.insert(insertStatement);
        myKnownRepositoryLocations.addVcs(name, id);
      }
      myConnection.commit();
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  private boolean checkForInMemory(MultiMap<String, String> map) {
    for (String vcsName : map.keySet()) {
      final Collection<String> paths = map.get(vcsName);
      for (Iterator<String> iterator = paths.iterator(); iterator.hasNext(); ) {
        final String path = iterator.next();
        if (myKnownRepositoryLocations.exists(vcsName, path)) {
          iterator.remove();
        }
      }
    }
    for (String paths : map.values()) {
      if (! paths.isEmpty()) return true;
    }
    return false;
  }

  private static String createStatementForTable(SqliteTables.BaseTable baseTable) {
    return "CREATE TABLE " + baseTable.TABLE_NAME + " ( ID INTEGER PRIMARY KEY, " + baseTable.getCreateTableStatement() + ");";
  }

  public void appendLists(final AbstractVcs vcs, final String root, final List<CommittedChangeList> lists) throws VcsException {
    //authors, revisions, paths
    if (lists.isEmpty()) return;
    assert myKnownRepositoryLocations.exists(vcs.getName(), root);
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), root);

    long maxRev = -1;
    long minRev = Long.MAX_VALUE;
    long maxTime = -1;
    long minTime = -1;
    final RevisionId firstRevData = myKnownRepositoryLocations.getFirstRevision(locationId);
    final Long firstRevision = firstRevData == null ? null : firstRevData.getNumber();
    final RevisionId lastRevData = myKnownRepositoryLocations.getLastRevision(locationId);
    final Long lastRevision = lastRevData == null ? null : lastRevData.getNumber();

    final List<List<CommittedChangeList>> split = new CollectionSplitter<CommittedChangeList>(20).split(lists);
    final Map<String, Long> knowPaths = new HashMap<>();
    for (List<CommittedChangeList> changeLists : split) {
      final Set<String> names = new HashSet<>();
      final Set<String> paths = new HashSet<>();
      for (Iterator<CommittedChangeList> iterator = changeLists.iterator(); iterator.hasNext(); ) {
        final CommittedChangeList list = iterator.next();
        final long number = list.getNumber();
        if (lastRevision != null && number <= lastRevision && number >= firstRevision) {
          iterator.remove();
          continue;
        }

        if (number > maxRev) {
          maxRev = number;
          maxTime = list.getCommitDate().getTime();
        }
        if (number < minRev) {
          minRev = number;
          minTime = list.getCommitDate().getTime();
        }

        names.add(list.getCommitterName()); // todo if null. also comment
        for (Change change : list.getChangesWithMovedTrees()) {
          if (change.getBeforeRevision() != null) {
            paths.add(getPath(change.getBeforeRevision()));
          }
          if (change.getAfterRevision() != null) {
            paths.add(getPath(change.getAfterRevision()));
          }
        }
      }
      final Map<String, Long> knownAuthors = myKnownRepositoryLocations.filterKnownAuthors(names);
      checkAndAddAuthors(names, knownAuthors);
      checkAndAddPaths(paths, knowPaths, locationId);
      insertChangeListsIfNotExists(vcs, knownAuthors, changeLists, locationId, knowPaths);

      if (firstRevision == null || minRev < firstRevision) {
        myKnownRepositoryLocations.setFirstRevision(locationId, new RevisionId(minRev, minTime));
      }
      if (lastRevision == null || maxRev > lastRevision) {
        myKnownRepositoryLocations.setLastRevision(locationId, new RevisionId(maxRev, maxTime));
      }
    }
  }

  private String getPath(ContentRevision revision) {
    final String path = FileUtil.toSystemIndependentName(revision.getFile().getPath());
    return path.endsWith("/") ? path : path + "/";
  }

  private Map<Long, CommittedChangeList> insertChangeListsIfNotExists(AbstractVcs vcs, final Map<String, Long> authors,
                                                                      final List<CommittedChangeList> lists,
                                                                      final long locationId, Map<String, Long> knowPaths)
    throws VcsException {
    final PreparedStatement statement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_REVISION,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("INSERT INTO " + SqliteTables.REVISION.TABLE_NAME + " ( " +
            StringUtil.join(Arrays.asList(SqliteTables.REVISION.ROOT_FK, SqliteTables.REVISION.AUTHOR_FK, SqliteTables.REVISION.DATE,
            SqliteTables.REVISION.NUMBER_INT, SqliteTables.REVISION.NUMBER_STR, SqliteTables.REVISION.COMMENT, SqliteTables.REVISION.COUNT, SqliteTables.REVISION.RAW_DATA), ", ") +
            ") VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
        }
      });
    final Map<Long, CommittedChangeList> result = new HashMap<>();
    final CachingCommittedChangesProvider provider = (CachingCommittedChangesProvider)vcs.getCommittedChangesProvider();
    try {
      statement.setLong(1, locationId);

      for (CommittedChangeList list : lists) {
        statement.setLong(2, authors.get(list.getCommitterName()));
        statement.setLong(3, list.getCommitDate().getTime());
        statement.setLong(4, list.getNumber());
        statement.setString(5, String.valueOf(list.getNumber()));
        statement.setString(6, list.getComment());
        statement.setLong(7, list.getChanges().size());
        final BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
        provider.writeChangeList(new DataOutputStream(stream), list);
        statement.setBytes(8, stream.toByteArray());
        final long id = SqliteUtil.insert(statement);
        result.put(id, list);

        insertPathsChanges(knowPaths, list, id);
      }
      myConnection.commit();
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
    return result;
  }

  private void insertPathsChanges(Map<String, Long> paths, CommittedChangeList list, long listId) throws VcsException {
    final PreparedStatement insert = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_PATH_2_REVS,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("INSERT INTO " + SqliteTables.PATHS_2_REVS.TABLE_NAME +
            " ( " + StringUtil.join(Arrays.asList(SqliteTables.PATHS_2_REVS.PATH_FK, SqliteTables.PATHS_2_REVS.REVISION_FK,
            SqliteTables.PATHS_2_REVS.TYPE, SqliteTables.PATHS_2_REVS.COPY_PATH_ID, SqliteTables.PATHS_2_REVS.DELETE_PATH_ID,
            SqliteTables.PATHS_2_REVS.VISIBLE), " , ") +
            ") VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
        }
      });
    try {
      insert.setLong(2, listId);
      final Collection<Change> withMoved = list.getChangesWithMovedTrees();
      final Set<Change> simple = new HashSet<>(list.getChanges());
      for (Change change : withMoved) {
        insertOneChange(paths, insert, change, simple.contains(change));
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  private void insertOneChange(Map<String, Long> paths, PreparedStatement insert, Change change, final boolean visible) throws SQLException {
    insert.setLong(6, visible ? 1 : 0);
    final ChangeTypeEnum type = ChangeTypeEnum.getChangeType(change);
    if (change.getBeforeRevision() == null) {
      // added, one path
      insert.setLong(1, paths.get(getPath(change.getAfterRevision())));
      insert.setLong(3, type.getCode());
      SqliteUtil.insert(insert);
    } else if (ChangeTypeEnum.MOVE.equals(type)) {
      // 2 paths
      final Long beforeId = paths.get(getPath(change.getBeforeRevision()));
      insert.setLong(1, beforeId);
      insert.setLong(3, ChangeTypeEnum.DELETE.getCode());
      SqliteUtil.insert(insert);

      insert.setLong(1, paths.get(getPath(change.getAfterRevision())));
      insert.setLong(4, beforeId);
      insert.setLong(3, type.getCode());
      SqliteUtil.insert(insert);
    } else if (change.getAfterRevision() == null) {
      insert.setLong(1, paths.get(getPath(change.getBeforeRevision())));
      insert.setLong(3, type.getCode());
      SqliteUtil.insert(insert);
    } else {
      // only after
      insert.setLong(1, paths.get(getPath(change.getAfterRevision())));
      insert.setLong(3, type.getCode());
      SqliteUtil.insert(insert);
    }
  }

  private void checkAndAddPaths(final Set<String> paths, final Map<String, Long> known, final Long locationId) throws VcsException {
    final PreparedStatement select = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_READ_PATH,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT " + SqliteTables.PATHS.ID + " FROM " + SqliteTables.PATHS.TABLE_NAME +
                                             " WHERE " + SqliteTables.PATHS.ROOT_FK + " = ? AND " + SqliteTables.PATHS.PATH + " = ?");
        }
      });
    try {
      select.setLong(1, locationId);
      for (final String path : paths) {
        select.setString(2, path);
        final ResultSet set = select.executeQuery();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            known.put(path, set.getLong(1));
          }
        });
      }

      paths.removeAll(known.keySet());
      if (paths.isEmpty()) return;

      final PreparedStatement insert = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_PATH,
        new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
          @Override
          public PreparedStatement convert(Connection connection) throws SQLException {
            return connection.prepareStatement("INSERT INTO " + SqliteTables.PATHS.TABLE_NAME + " ( " +
              SqliteTables.PATHS.ROOT_FK + " , " + SqliteTables.PATHS.PATH + " ) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
          }
        });
      insert.setLong(1, locationId);
      for (String path : paths) {
        insert.setString(2, path);
        final long id = SqliteUtil.insert(insert);
        known.put(path, id);
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  private void checkAndAddAuthors(final Set<String> names, final Map<String, Long> known) throws VcsException {
    final PreparedStatement statement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_FILTER_KNOWN_AUTHORS,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT " + SqliteTables.AUTHOR.ID + ", " + SqliteTables.AUTHOR.NAME +
                                             " FROM " + SqliteTables.AUTHOR.TABLE_NAME + " WHERE " + SqliteTables.AUTHOR.NAME + "=?");
        }
      });

    try {
      for (final Iterator<String> iterator = names.iterator(); iterator.hasNext(); ) {
        final String name = iterator.next();
        statement.setString(1, name);
        final ResultSet set = statement.executeQuery();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long id = set.getLong(SqliteTables.AUTHOR.ID);
            myKnownRepositoryLocations.addKnownAuthor(name, id);
            known.put(name, id);
            iterator.remove();
          }
        });
      }
      if (names.isEmpty()) return;

      final PreparedStatement insertAuthor = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_ADD_AUTHOR,
        new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
          @Override
          public PreparedStatement convert(Connection connection) throws SQLException {
            return connection.prepareStatement("INSERT INTO " + SqliteTables.AUTHOR.TABLE_NAME + " ( " + SqliteTables.AUTHOR.NAME +
                                               ") VALUES (?)", Statement.RETURN_GENERATED_KEYS);
          }
        });
      for (String name : names) {
        insertAuthor.setString(1, name);
        final long id = SqliteUtil.insert(insertAuthor);
        myKnownRepositoryLocations.addKnownAuthor(name, id);
        known.put(name, id);
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  public RevisionId getFirstRevision(final AbstractVcs vcs, final String root) {
    final String systemIndependent = FileUtil.toSystemIndependentName(root);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), systemIndependent)) {
      return RevisionId.FAKE;
    }
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), systemIndependent);
    return myKnownRepositoryLocations.getFirstRevision(locationId);
  }

  @NotNull
  public RevisionId getLastRevision(final AbstractVcs vcs, final String root) {
    final String systemIndependent = FileUtil.toSystemIndependentName(root);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), systemIndependent)) {
      return RevisionId.FAKE;
    }
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), systemIndependent);
    return myKnownRepositoryLocations.getLastRevision(locationId);
  }

  // alternatively, we can use usual lists + a map marking incoming
  public List<ReceivedChangeList> selectIncoming(final AbstractVcs vcs, final RepositoryLocation location) throws VcsException {
    final Map<Long, CommittedChangeList> full = new HashMap<>();
    final TreeMap<Long, Set<String>> incomingPaths = new TreeMap<>();
    final long locationId = getLocationId(vcs, location);

    final PreparedStatement select = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_INCOMING,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          // todo control what is selected
          return connection.prepareStatement("SELECT R." + SqliteTables.REVISION.NUMBER_INT + " , R." + SqliteTables.REVISION.RAW_DATA +
            " , P." + SqliteTables.PATHS.PATH +
            " FROM " + SqliteTables.INCOMING_PATHS.TABLE_NAME + "I INNER JOIN " +
            SqliteTables.PATHS_2_REVS.TABLE_NAME + "PR ON I." + SqliteTables.INCOMING_PATHS.PR_FK + " = PR." +
            SqliteTables.PATHS_2_REVS.ID + ", " + SqliteTables.REVISION.TABLE_NAME + " R ON PR." + SqliteTables.PATHS_2_REVS.REVISION_FK +
            " = R." + SqliteTables.REVISION.ID + " , " + SqliteTables.PATHS_2_REVS.TABLE_NAME + "P ON PR." + SqliteTables.PATHS_2_REVS.PATH_FK +
            " = P." + SqliteTables.PATHS.ID + " WHERE R." + SqliteTables.REVISION.ROOT_FK + "=?");
        }
      });
    final CachingCommittedChangesProvider provider = vcs.getCachingCommittedChangesProvider();
    try {
      select.setLong(1, locationId);
      final ResultSet set = select.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          final long revNum = set.getLong("R." + SqliteTables.REVISION.NUMBER_INT);
          Set<String> paths = incomingPaths.get(revNum);
          if (paths == null) {
            final byte[] bytes = set.getBytes("R." + SqliteTables.REVISION.RAW_DATA);
            final CommittedChangeList nativeList = readListByProvider(bytes, provider, location);
            full.put(revNum, nativeList);
            paths = new HashSet<>();
            incomingPaths.put(revNum, paths);
          }
          final String path = set.getString("P." + SqliteTables.PATHS.PATH);
          paths.add(path);
        }
      });
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
    final List<ReceivedChangeList> result = new ArrayList<>();
    for (Map.Entry<Long, Set<String>> entry : incomingPaths.entrySet()) {
      final Long revNum = entry.getKey();

    }
    // TODO continue here
    // TODO continue here
    // TODO continue here
    // TODO continue here
    // TODO continue here

    return null;
   // return new ArrayList<ReceivedChangeList>(incomingPaths.descendingMap().values());
  }

  private long getLocationId(AbstractVcs vcs, RepositoryLocation location) {
    final String normalizedLocation = normalizeLocation(location);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), normalizedLocation)) {
      assert false;
    }
    return myKnownRepositoryLocations.getLocationId(vcs.getName(), normalizedLocation);
  }

  public void insertIncoming(final AbstractVcs vcs, final RepositoryLocation location, long pathId, final long lastRev, final long oldRev)
    throws VcsException {
    assert lastRev > 0 || oldRev > 0;
    final long locationId = getLocationId(vcs, location);

    if (lastRev > 0 && oldRev > 0) {
      final PreparedStatement insertBoth = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_INCOMING,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("INSERT INTO " + SqliteTables.INCOMING_PATHS.TABLE_NAME +
            " ( " + SqliteTables.INCOMING_PATHS.PR_FK + " ) VALUES (SELECT " + SqliteTables.PATHS_2_REVS.ID + " FROM " +
            SqliteTables.PATHS_2_REVS.TABLE_NAME + "PR INNER JOIN " + SqliteTables.REVISION.TABLE_NAME + " R ON R." +
            SqliteTables.REVISION.ID + "=PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + " WHERE R." +
            SqliteTables.REVISION.NUMBER_INT + "<=? AND R." + SqliteTables.REVISION.NUMBER_INT + ">=? AND R." + SqliteTables.REVISION.ROOT_FK +
            "=? AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + "=?)");
        }
      });
      try {
        insertBoth.setLong(1, lastRev);
        insertBoth.setLong(2, oldRev);
        insertBoth.setLong(3, locationId);
        insertBoth.setLong(4, pathId);
        final int numRows = insertBoth.executeUpdate();
        return;
      }
      catch (SQLException e) {
        throw new VcsException(e);
      }
    }

    if (lastRev > 0) {
      final PreparedStatement insertOnlyLast = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_INCOMING,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("INSERT INTO " + SqliteTables.INCOMING_PATHS.TABLE_NAME +
            " ( " + SqliteTables.INCOMING_PATHS.PR_FK + " ) VALUES (SELECT " + SqliteTables.PATHS_2_REVS.ID + " FROM " +
            SqliteTables.PATHS_2_REVS.TABLE_NAME + "PR INNER JOIN " + SqliteTables.REVISION.TABLE_NAME + " R ON R." +
            SqliteTables.REVISION.ID + "=PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + " WHERE R." +
            SqliteTables.REVISION.NUMBER_INT + "<=? AND R." + SqliteTables.REVISION.ROOT_FK +
            "=? AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + "=?)");
        }
      });
      try {
        insertOnlyLast.setLong(1, lastRev);
        insertOnlyLast.setLong(2, locationId);
        insertOnlyLast.setLong(3, pathId);
        final int numRows = insertOnlyLast.executeUpdate();
        return;
      }
      catch (SQLException e) {
        throw new VcsException(e);
      }
    }
    // first rev > 0
    final PreparedStatement insertOnlyFirst = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_INSERT_INCOMING,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("INSERT INTO " + SqliteTables.INCOMING_PATHS.TABLE_NAME +
            " ( " + SqliteTables.INCOMING_PATHS.PR_FK + " ) VALUES (SELECT " + SqliteTables.PATHS_2_REVS.ID + " FROM " +
            SqliteTables.PATHS_2_REVS.TABLE_NAME + "PR INNER JOIN " + SqliteTables.REVISION.TABLE_NAME + " R ON R." +
            SqliteTables.REVISION.ID + "=PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + " WHERE R." + SqliteTables.REVISION.NUMBER_INT +
            ">=? AND R." + SqliteTables.REVISION.ROOT_FK +
            "=? AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + "=?)");
        }
      });
    try {
      insertOnlyFirst.setLong(1, oldRev);
      insertOnlyFirst.setLong(2, locationId);
      insertOnlyFirst.setLong(3, pathId);
      final int numRows = insertOnlyFirst.executeUpdate();
      return;
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  public List<CommittedChangeList> readLists(final AbstractVcs vcs, final RepositoryLocation location,
                                             final RevisionId last, final RevisionId old, final String subfolder) throws VcsException {
    final String root = normalizeLocation(location);
    final RevisionId lastExisitngData = getLastRevision(vcs, root);
    final RevisionId firstExistingData = getFirstRevision(vcs, root);

    if (lastExisitngData.isFake() || firstExistingData.isFake()) return Collections.emptyList();

    final SelectListsQueryHelper helper =
      new SelectListsQueryHelper(myConnection, lastExisitngData, firstExistingData, last, old, getLocationId(vcs, location), subfolder);
    final List<CommittedChangeList> result = new ArrayList<>();
    try {
      final PreparedStatement statement = helper.createStatement();
      final CachingCommittedChangesProvider provider = (CachingCommittedChangesProvider)vcs.getCommittedChangesProvider();
      final ResultSet set = statement.executeQuery();
      final Set<Long> controlSet = new HashSet<>();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          final long number = set.getLong(SqliteTables.REVISION.NUMBER_INT);
          if (controlSet.contains(number)) {
            return;
          }
          controlSet.add(number);
          final byte[] bytes = set.getBytes(SqliteTables.REVISION.RAW_DATA);
          final CommittedChangeList list = readListByProvider(bytes, provider, location);
          result.add(list);
        }
      });
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }

    return result;
  }

  public List<CommittedChangeList> readLists(final AbstractVcs vcs, final RepositoryLocation location, final long lastRev, final long oldRev)
    throws VcsException {
    final String root = normalizeLocation(location);
    final long lastExisting = getLastRevision(vcs, root).getNumber();
    final long firstExisting = getFirstRevision(vcs, root).getNumber();

    if (lastExisting == -1 || firstExisting == -1) return Collections.emptyList();
    final long operatingFirst = oldRev == -1 ? firstExisting : oldRev;
    final long operatingLast = lastRev == -1 ? lastExisting : lastRev;

    final PreparedStatement statement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_REVISIONS,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          // "real" statement - will be used when each committed changes provider will have the method to restore revision uniformly, through changed paths + #
          // maybe it's safier to call left outer join, but for current VCSes we always have at least one path changed for each revision -> inner join is preferable
          /*return connection.prepareStatement("SELECT * FROM " + SqliteTables.REVISION.TABLE_NAME + "R , " +
            SqliteTables.PATHS + "P , "+ SqliteTables.AUTHOR + "A INNER JOIN " + SqliteTables.PATHS_2_REVS + "PR ON PR." +
            SqliteTables.PATHS_2_REVS.REVISION_FK + "=R." + SqliteTables.REVISION.ID + " AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK +
            "=" + SqliteTables.PATHS.ID + " AND R." + SqliteTables.REVISION.AUTHOR_FK + "=A." + SqliteTables.AUTHOR.ID +
            " WHERE R." + SqliteTables.REVISION.NUMBER_INT + ">=? AND R." + SqliteTables.REVISION.NUMBER_INT + "<=?");*/
          //1=first, 2=last

          return connection.prepareStatement("SELECT * FROM " + SqliteTables.REVISION.TABLE_NAME + " WHERE " +
            SqliteTables.REVISION.NUMBER_INT + ">=? AND " + SqliteTables.REVISION.NUMBER_INT + "<=? ORDER BY " + SqliteTables.REVISION.NUMBER_INT
          + " DESC");
        }
      });
    final List<CommittedChangeList> result = new ArrayList<>();
    try {

      statement.setLong(1, operatingFirst);
      statement.setLong(2, operatingLast);
      final CachingCommittedChangesProvider provider = (CachingCommittedChangesProvider)vcs.getCommittedChangesProvider();
      final ResultSet set = statement.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          final byte[] bytes = set.getBytes(SqliteTables.REVISION.RAW_DATA);
          final CommittedChangeList list = readListByProvider(bytes, provider, location);
          result.add(list);

          /*final long revisionId = set.getLong("R." + SqliteTables.REVISION.ID);
          CommittedChangeList list = lists.get(revisionId);
          if (list == null) {
            final long numberLong = set.getLong("R." + SqliteTables.REVISION.NUMBER_INT);
            final String numberStr = set.getString("R." + SqliteTables.REVISION.NUMBER_STR);
            final String comment = set.getString("R." + SqliteTables.REVISION.COMMENT);
            final Long date = set.getLong("R." + SqliteTables.REVISION.DATE);
            final String author = set.getString("A." + SqliteTables.REVISION.AUTHOR_FK);
            list = new CommittedChangeListImpl("", comment, author, numberLong, new Date(date), Collections.<Change>emptyList());
          }*/
        }
      });
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
    return result;
  }

  private CommittedChangeList readListByProvider(byte[] bytes, CachingCommittedChangesProvider provider, RepositoryLocation location)
    throws SQLException {
    final CommittedChangeList list;
    try {
      list = provider.readChangeList(location, new DataInputStream(new ByteArrayInputStream(bytes)));
    }
    catch (IOException e) {
      throw new SQLException(e);
    }
    return list;
  }

  public PathState getPathState(final AbstractVcs vcs, final RepositoryLocation location, final String path) throws VcsException {
    String normalizedPath = FileUtil.toSystemIndependentName(path);
    normalizedPath = normalizedPath.endsWith("/") ? normalizedPath : normalizedPath + "/";
    final String normalizedLocation = normalizeLocation(location);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), normalizedLocation)) return null;

    final PreparedStatement maxStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_PATH_DATA,
                                                                                     new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
                                                                                       @Override
                                                                                       public PreparedStatement convert(Connection connection)
                                                                                         throws SQLException {
                                                                                         final String innerQuery = "SELECT MAX(R." +
                                                                                                                   SqliteTables.REVISION.NUMBER_INT +
                                                                                                                   ") MAX FROM " +
                                                                                                                   SqliteTables.PATHS_2_REVS.TABLE_NAME +
                                                                                                                   " PR INNER JOIN " +
                                                                                                                   SqliteTables.REVISION.TABLE_NAME +
                                                                                                                   " R, " +
                                                                                                                   SqliteTables.PATHS.TABLE_NAME +
                                                                                                                   " P ON PR." +
                                                                                                                   SqliteTables.PATHS_2_REVS.REVISION_FK +
                                                                                                                   "=R." +
                                                                                                                   SqliteTables.REVISION.ID +
                                                                                                                   " AND PR." +
                                                                                                                   SqliteTables.PATHS_2_REVS.PATH_FK +
                                                                                                                   "=P." +
                                                                                                                   SqliteTables.PATHS.ID +
                                                                                                                   " WHERE P." +
                                                                                                                   SqliteTables.PATHS.PATH +
                                                                                                                   "=? AND R." +
                                                                                                                   SqliteTables.REVISION.ROOT_FK +
                                                                                                                   "=?";

                                                                                         return connection.prepareStatement("SELECT R." +
                                                                                                                            SqliteTables.REVISION.NUMBER_INT +
                                                                                                                            " REV_NU, PR." +
                                                                                                                            SqliteTables.PATHS_2_REVS.TYPE +
                                                                                                                            " TYPE FROM " +
                                                                                                                            SqliteTables.PATHS_2_REVS.TABLE_NAME +
                                                                                                                            " PR INNER JOIN " +
                                                                                                                            SqliteTables.REVISION.TABLE_NAME +
                                                                                                                            " R, " +
                                                                                                                            SqliteTables.PATHS.TABLE_NAME +
                                                                                                                            " P ON PR." +
                                                                                                                            SqliteTables.PATHS_2_REVS.REVISION_FK +
                                                                                                                            "=R." +
                                                                                                                            SqliteTables.REVISION.ID +
                                                                                                                            " AND PR." +
                                                                                                                            SqliteTables.PATHS_2_REVS.PATH_FK +
                                                                                                                            "=P." +
                                                                                                                            SqliteTables.PATHS.ID +
                                                                                                                            " WHERE P." +
                                                                                                                            SqliteTables.PATHS.PATH +
                                                                                                                            "=? AND R." +
                                                                                                                            SqliteTables.REVISION.ROOT_FK +
                                                                                                                            "=? AND R." +
                                                                                                                            SqliteTables.REVISION.NUMBER_INT +
                                                                                                                            " = (" +
                                                                                                                            innerQuery +
                                                                                                                            ")");
                                                                                       }
                                                                                     });

    try {
      maxStatement.setString(1, normalizedPath);
      maxStatement.setString(3, normalizedPath);
      final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), normalizedLocation);
      maxStatement.setLong(2, locationId);
      maxStatement.setLong(4, locationId);
      final long type[] = new long[1];
      final long maxRev[] = new long[1];
      maxRev[0] = -1;
      final ResultSet set = maxStatement.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          maxRev[0] = set.getLong("REV_NU");
          type[0] = set.getLong("TYPE");
        }
      });

      if (maxRev[0] <= 0) return null;
      if (type[0] == -100) return null;
      final ChangeTypeEnum changeType = ChangeTypeEnum.getChangeType(type[0]);
      if (changeType == null) return null;
      return new PathState(maxRev[0], ! ChangeTypeEnum.DELETE.equals(changeType));
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  private String normalizeLocation(RepositoryLocation location) {
    return FileUtil.toSystemIndependentName(location.toPresentableString());
  }

  // this is batch one
  /*public <T> void getLastRevisionsForPath(final AbstractVcs vcs, final RepositoryLocation location,
                                          final Convertor<T, String> pathConvertor, Set<T> files, final PairConsumer<T, PathState> consumer)
    throws VcsException {
    final String normalizedLocation = normalizeLocation(location);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), normalizedLocation)) return;
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), normalizedLocation);

    if (files.size() < ourLastPathRevisionBatchSize) {
      iterateGetPathState(vcs, location, pathConvertor, files, consumer);
      return;
    }

    String s = StringUtil.repeat("?,", ourLastPathRevisionBatchSize);
    final String repeat = s.substring(0, s.length() - 1);
    final PreparedStatement maxStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_PATH_DATA_BATCH,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT MAX(R." + SqliteTables.REVISION.NUMBER_INT + ") MAX, P."+ SqliteTables.PATHS.PATH +
                      " PATH, P." + SqliteTables.PATHS.ID + " PATH_ID FROM " + SqliteTables.PATHS_2_REVS.TABLE_NAME + " PR INNER JOIN " +
                      SqliteTables.REVISION.TABLE_NAME + " R, " + SqliteTables.PATHS.TABLE_NAME + " P ON PR." +
                      SqliteTables.PATHS_2_REVS.REVISION_FK + "=R." +
                      SqliteTables.REVISION.ID + " AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + "=P." + SqliteTables.PATHS.ID +
                      " WHERE P." + SqliteTables.PATHS.PATH + " IN (" + repeat + ") AND R." + SqliteTables.REVISION.ROOT_FK + "=?");
        }
      });
    final PreparedStatement typeStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_PATHS_2_REVS_BATCH,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT PR." + SqliteTables.PATHS_2_REVS.TYPE + " TYPE, R." +
            SqliteTables.REVISION.NUMBER_INT + " REV_NUM, PR." + SqliteTables.PATHS_2_REVS.PATH_FK + " PATH_ID " +
            " FROM " + SqliteTables.PATHS_2_REVS.TABLE_NAME +
            " PR INNER JOIN " + SqliteTables.REVISION.TABLE_NAME + " R ON PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + "=R." +
            SqliteTables.REVISION.ID + " WHERE R." + SqliteTables.REVISION.NUMBER_INT + " IN (" + repeat +
            ") AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + " IN(" + repeat + ")");
        }
      });
    final List<List<T>> split = new CollectionSplitter<T>(ourLastPathRevisionBatchSize).split(files);
    try {
      maxStatement.setLong(ourLastPathRevisionBatchSize + 1, locationId);
      for (List<T> list : split) {
        final Map<String, T> paths2elements = new HashMap<String, T>();
        final int size = list.size();
        if (size < ourLastPathRevisionBatchSize) {
          iterateGetPathState(vcs, location, pathConvertor, list, consumer);
          return;
        }
        for (int i = 0; i < size; i++) {
          T t = list.get(i);
          final String convert = pathConvertor.convert(t);
          assert ! paths2elements.containsKey(convert);
          paths2elements.put(convert, t);
          maxStatement.setString(i + 1, convert);
        }

        final ResultSet set = maxStatement.executeQuery();
        final Map<Long, Pair<Long, String>> maxMap = new HashMap<Long, Pair<Long, String>>();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long maxRev = set.getLong("MAX");
            final String path = set.getString("PATH");
            final long pathId = set.getLong("PATH_ID");
            maxMap.put(pathId, Pair.create(maxRev, path));
          }
        });

        int i = 0;
        for (Map.Entry<Long, Pair<Long, String>> entry : maxMap.entrySet()) {
          typeStatement.setLong(i + 1, entry.getValue().getFirst());  // rev #
          typeStatement.setLong(ourLastPathRevisionBatchSize + i + 1, entry.getKey()); // path id
          ++ i;
        }
        final ResultSet detailsSet = typeStatement.executeQuery();
        SqliteUtil.readSelectResults(detailsSet, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long type = detailsSet.getLong("TYPE");
            final ChangeTypeEnum changeType = ChangeTypeEnum.getChangeType(type);
            if (changeType == null) {
              LOG.info("Illegal change type: " + type);
              return;
            }

            final long revNum = detailsSet.getLong("REV_NUM");
            final long pathId = detailsSet.getLong("PATH_ID");

            final Pair<Long, String> pair = maxMap.get(pathId);
            if (pair.getFirst() == revNum) {
              consumer.consume(paths2elements.get(pair.getSecond()), new PathState(revNum, ! ChangeTypeEnum.DELETE.equals(changeType)));
            }
          }
        });
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }*/

  /*private <T> void iterateGetPathState(AbstractVcs vcs,
                                       RepositoryLocation location,
                                       Convertor<T, String> pathConvertor,
                                       Collection<T> files,
                                       PairConsumer<T, PathState> consumer) throws VcsException {
    for (T file : files) {
      final PathState state = getPathState(vcs, location, pathConvertor.convert(file));
      consumer.consume(file, state);
    }
  }*/
}
