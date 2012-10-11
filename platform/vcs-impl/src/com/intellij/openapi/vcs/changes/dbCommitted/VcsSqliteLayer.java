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
 * TODO quotations!
 * TODO quotations!
 * TODO quotations!
 * TODO quotations!
 * TODO quotations!
 * TODO quotations!
 */
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.DataOutputStream;

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
  private final KnownRepositoryLocations myKnownRepositoryLocations;
  private final CacheJdbcConnection myConnection;

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

    final MultiMap<String, String> copy = new MultiMap<String, String>();
    copy.putAllValues(vcses);
    if (! checkForInMemory(copy)) return;

    final HashSet<String> vcsNamesSet = new HashSet<String>(vcses.keySet());
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
    final PreparedStatement statement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_MAX_REVISION,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT MAX(" + SqliteTables.REVISION.NUMBER_INT + ") , MIN(" + SqliteTables.REVISION.NUMBER_INT + ") " +
                                             " FROM " + SqliteTables.REVISION.TABLE_NAME + " WHERE " + SqliteTables.REVISION.ROOT_FK + " =?");
        }
      });
    try {
      for (final Long id : rootIdsToCheck) {
        statement.setLong(1, id);
        final ResultSet set = statement.executeQuery();
        SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
          @Override
          public void run() throws SQLException {
            final long max = set.getLong(1);
            final long min = set.getLong(2);
            if (max > 0) {// 0 is === SQL NULL
              myKnownRepositoryLocations.setLastRevision(id, max);
              myKnownRepositoryLocations.setFirstRevision(id, min);
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
    final Set<Long> idsToCheck = new HashSet<Long>();
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
            copy.removeValue(vcsName, url);
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
    final Long firstRevision = myKnownRepositoryLocations.getFirstRevision(locationId);
    final Long lastRevision = myKnownRepositoryLocations.getLastRevision(locationId);

    final List<List<CommittedChangeList>> split = new CollectionSplitter<CommittedChangeList>(20).split(lists);
    final Map<String, Long> knowPaths = new HashMap<String, Long>();
    for (List<CommittedChangeList> changeLists : split) {
      final Set<String> names = new HashSet<String>();
      final Set<String> paths = new HashSet<String>();
      for (Iterator<CommittedChangeList> iterator = changeLists.iterator(); iterator.hasNext(); ) {
        final CommittedChangeList list = iterator.next();
        if (lastRevision != null && list.getNumber() <= lastRevision && list.getNumber() >= firstRevision) {
          iterator.remove();
          continue;
        }

        maxRev = Math.max(maxRev, list.getNumber());
        minRev = Math.min(minRev, list.getNumber());

        names.add(list.getCommitterName()); // todo if null. also comment
        for (Change change : list.getChanges()) {
          if (change.getBeforeRevision() != null) {
            paths.add(FileUtil.toSystemIndependentName(change.getBeforeRevision().getFile().getPath()));
          }
          if (change.getAfterRevision() != null) {
            paths.add(FileUtil.toSystemIndependentName(change.getAfterRevision().getFile().getPath()));
          }
        }
      }
      final Map<String, Long> knownAuthors = myKnownRepositoryLocations.filterKnownAuthors(names);
      checkAndAddAuthors(names, knownAuthors);
      checkAndAddPaths(paths, knowPaths, locationId);
      insertChangeListsIfNotExists(vcs, knownAuthors, changeLists, locationId, knowPaths);

      if (firstRevision == null || minRev < firstRevision) {
        myKnownRepositoryLocations.setFirstRevision(locationId, minRev);
      }
      if (lastRevision == null || maxRev > lastRevision) {
        myKnownRepositoryLocations.setLastRevision(locationId, maxRev);
      }
    }
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
                                                             SqliteTables.REVISION.NUMBER_INT, SqliteTables.REVISION.NUMBER_STR, SqliteTables.REVISION.COMMENT, SqliteTables.REVISION.RAW_DATA), ", ") +
                                             ") VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
        }
      });
    final Map<Long, CommittedChangeList> result = new HashMap<Long, CommittedChangeList>();
    final CachingCommittedChangesProvider provider = (CachingCommittedChangesProvider)vcs.getCommittedChangesProvider();
    try {
      statement.setLong(1, locationId);

      for (CommittedChangeList list : lists) {
        statement.setLong(2, authors.get(list.getCommitterName()));
        statement.setLong(3, list.getCommitDate().getTime());
        statement.setLong(4, list.getNumber());
        statement.setString(5, String.valueOf(list.getNumber()));
        statement.setString(6, list.getComment());
        final BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
        provider.writeChangeList(new DataOutputStream(stream), list);
        statement.setBytes(7, stream.toByteArray());
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
            SqliteTables.PATHS_2_REVS.TYPE, SqliteTables.PATHS_2_REVS.COPY_PATH_ID, SqliteTables.PATHS_2_REVS.DELETE_PATH_ID), " , ") +
            ") VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
        }
      });
    try {
      insert.setLong(2, listId);
      for (Change change : list.getChanges()) {
        final ChangeTypeEnum type = ChangeTypeEnum.getChangeType(change);
        if (change.getBeforeRevision() == null) {
          // added, one path
          insert.setLong(1, paths.get(FileUtil.toSystemIndependentName(change.getAfterRevision().getFile().getPath())));
          insert.setLong(3, type.getCode());
          SqliteUtil.insert(insert);
        } else if (ChangeTypeEnum.MOVE.equals(type)) {
          // 2 paths
          final Long beforeId = paths.get(FileUtil.toSystemIndependentName(change.getBeforeRevision().getFile().getPath()));
          insert.setLong(1, beforeId);
          insert.setLong(3, ChangeTypeEnum.DELETE.getCode());
          SqliteUtil.insert(insert);

          insert.setLong(1, paths.get(FileUtil.toSystemIndependentName(change.getAfterRevision().getFile().getPath())));
          insert.setLong(4, beforeId);
          insert.setLong(3, type.getCode());
          SqliteUtil.insert(insert);
        } else if (change.getAfterRevision() == null) {
          insert.setLong(1, paths.get(FileUtil.toSystemIndependentName(change.getBeforeRevision().getFile().getPath())));
          insert.setLong(3, type.getCode());
          SqliteUtil.insert(insert);
        } else {
          // only after
          insert.setLong(1, paths.get(FileUtil.toSystemIndependentName(change.getAfterRevision().getFile().getPath())));
          insert.setLong(3, type.getCode());
          SqliteUtil.insert(insert);
        }
      }
    }
    catch (SQLException e) {
      throw new VcsException(e);
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

  public long getFirstRevision(final AbstractVcs vcs, final String root) {
    final String systemIndependent = FileUtil.toSystemIndependentName(root);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), systemIndependent)) {
      return -1;
    }
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), systemIndependent);
    final Long firstRevision = myKnownRepositoryLocations.getFirstRevision(locationId);
    return firstRevision == null ? -1 : firstRevision.longValue();
  }

  public long getLastRevision(final AbstractVcs vcs, final String root) {
    final String systemIndependent = FileUtil.toSystemIndependentName(root);
    if (! myKnownRepositoryLocations.exists(vcs.getName(), systemIndependent)) {
      return -1;
    }
    final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), systemIndependent);
    final Long lastRevision = myKnownRepositoryLocations.getLastRevision(locationId);
    return lastRevision == null ? -1 : lastRevision.longValue();
  }

  public List<CommittedChangeList> readLists(final AbstractVcs vcs, final RepositoryLocation location, final long lastRev, final long oldRev)
    throws VcsException {
    final String root = FileUtil.toSystemIndependentName(location.toPresentableString());
    final long lastExisting = getLastRevision(vcs, root);
    final long firstExisting = getFirstRevision(vcs, root);

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
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    try {

      statement.setLong(1, operatingFirst);
      statement.setLong(2, operatingLast);
      final CachingCommittedChangesProvider provider = (CachingCommittedChangesProvider)vcs.getCommittedChangesProvider();
      final ResultSet set = statement.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          final byte[] bytes = set.getBytes(SqliteTables.REVISION.RAW_DATA);
          try {
            final CommittedChangeList list = provider.readChangeList(location, new DataInputStream(new ByteArrayInputStream(bytes)));
            result.add(list);
          }
          catch (IOException e) {
            throw new SQLException(e);
          }

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

  public PathState getPathState(final AbstractVcs vcs, final RepositoryLocation location, final String path) throws VcsException {
    final String normalizedPath = FileUtil.toSystemIndependentName(path);
    final String normalizedLocation = FileUtil.toSystemIndependentName(location.toPresentableString());
    if (! myKnownRepositoryLocations.exists(vcs.getName(), normalizedLocation)) return null;

    final PreparedStatement maxStatement = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_SELECT_PATH_DATA,
      new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
        @Override
        public PreparedStatement convert(Connection connection) throws SQLException {
          return connection.prepareStatement("SELECT MAX(R." + SqliteTables.REVISION.NUMBER_INT + ") MAX, MAX(P."+ SqliteTables.PATHS.ID +
            ") PATH_ID FROM " + SqliteTables.PATHS_2_REVS.TABLE_NAME + " PR INNER JOIN " +
            SqliteTables.REVISION.TABLE_NAME + " R, " + SqliteTables.PATHS.TABLE_NAME + " P ON PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + "=R." +
            SqliteTables.REVISION.ID + " AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK + "=P." + SqliteTables.PATHS.ID +
            " WHERE P." + SqliteTables.PATHS.PATH + "=? AND R." + SqliteTables.REVISION.ROOT_FK + "=?");
        }
      });

    try {
      maxStatement.setString(1, normalizedPath);
      final long locationId = myKnownRepositoryLocations.getLocationId(vcs.getName(), normalizedLocation);
      maxStatement.setLong(2, locationId);
      final long pathId[] = new long[1];
      final long maxRev[] = new long[1];
      maxRev[0] = -1;
      final ResultSet set = maxStatement.executeQuery();
      SqliteUtil.readSelectResults(set, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          maxRev[0] = set.getLong("MAX");
          pathId[0] = set.getLong("PATH_ID");
        }
      });

      if (maxRev[0] == -1) return null;

      final PreparedStatement lastChangeType = myConnection.getOrCreatePreparedStatement(SqliteTables.PREPARED_PATHS_2_REVS,
        new ThrowableConvertor<Connection, PreparedStatement, SQLException>() {
          @Override
          public PreparedStatement convert(Connection connection) throws SQLException {
            return connection.prepareStatement("SELECT PR." + SqliteTables.PATHS_2_REVS.TYPE + " FROM " + SqliteTables.PATHS_2_REVS.TABLE_NAME +
              " PR INNER JOIN " + SqliteTables.REVISION.TABLE_NAME + " R ON PR." + SqliteTables.PATHS_2_REVS.REVISION_FK + "=R." +
              SqliteTables.REVISION.ID + " WHERE R." + SqliteTables.REVISION.NUMBER_INT + "=? AND PR." + SqliteTables.PATHS_2_REVS.PATH_FK +
              "=?");
          }
        });
      lastChangeType.setLong(1, maxRev[0]);
      lastChangeType.setLong(2, pathId[0]);
      final ResultSet typeSet = lastChangeType.executeQuery();
      final long[] type = new long[1];
      type[0] = -100;
      SqliteUtil.readSelectResults(typeSet, new ThrowableRunnable<SQLException>() {
        @Override
        public void run() throws SQLException {
          type[0] = typeSet.getLong(1);
        }
      });
      if (type[0] == -100) return null;
      final ChangeTypeEnum changeType = ChangeTypeEnum.getChangeType(type[0]);
      if (changeType == null) return null;
      return new PathState(maxRev[0], ! ChangeTypeEnum.DELETE.equals(changeType));
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }
}
