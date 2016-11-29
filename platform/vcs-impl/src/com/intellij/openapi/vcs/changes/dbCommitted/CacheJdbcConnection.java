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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/9/12
 * Time: 10:36 PM
 */
public class CacheJdbcConnection {
  private final Object myLock;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.dbCommitted.CacheJdbcConnection");
  private Connection myConnection;
  private final Map<String, PreparedStatement> myPreparedStatementsMap;
  private final File myDbFile;
  private final ThrowableConsumer<Connection, VcsException> myInitDbScript;

  public CacheJdbcConnection(final File dbFile, final ThrowableConsumer<Connection, VcsException> initDbScript) {
    myDbFile = dbFile;
    myInitDbScript = initDbScript;
    myLock = new Object();
    myPreparedStatementsMap = new HashMap<>();
  }

  public void closeConnection() {
    final HashMap<String, PreparedStatement> copyMap;
    final Connection connection;
    synchronized (myLock) {
      copyMap = new HashMap<>(myPreparedStatementsMap);
      connection = myConnection;
      myConnection = null;
      myPreparedStatementsMap.clear();
    }
    if (connection != null) {
      for (PreparedStatement statement : copyMap.values()) {
        try {
          statement.close();
        }
        catch (SQLException e) {
          LOG.info(e);
        }
      }
      try {
        connection.close();
      }
      catch (SQLException e) {
        LOG.info(e);
      }
    }
  }

  public PreparedStatement getOrCreatePreparedStatement(@NotNull final String name, final ThrowableConvertor<Connection, PreparedStatement, SQLException> getter)
    throws VcsException {
    synchronized (myLock) {
      getConnection();
      final PreparedStatement statement = myPreparedStatementsMap.get(name);
      if (statement != null) {
        return statement;
      }
      final PreparedStatement newStatement;
      try {
        newStatement = getter.convert(myConnection);
      }
      catch (SQLException e) {
        throw new VcsException(e);
      }
      myPreparedStatementsMap.put(name, newStatement);
      return newStatement;
    }
  }

  public Connection getConnection() throws VcsException {
    synchronized (myLock) {
      if (myConnection == null) {
        myConnection = initConnection();
      }
      return myConnection;
    }
  }

  private Connection initConnection() throws VcsException {
    final boolean existed = myDbFile.exists();
    try {
      Class.forName("org.sqlite.JDBC");
      final Connection connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", myDbFile.getPath()));
      if (! existed && myInitDbScript != null) {
        // ok to run under lock => no read is possible until initialized
        myInitDbScript.consume(connection);
      }
      connection.setAutoCommit(false);
      return connection;
    }
    catch (final ClassNotFoundException e) {
      throw new VcsException(e);
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }

  public void commit() throws VcsException {
    try {
      getConnection().commit();
    }
    catch (SQLException e) {
      throw new VcsException(e);
    }
  }
}
