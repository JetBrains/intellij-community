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
package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorkerImpl;
import com.intellij.cvsSupport2.connections.ssh.SolveableAuthenticationException;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.cvsSupport2.util.CvsFileUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;
import org.netbeans.lib.cvsclient.connection.UnknownUserException;

import java.io.*;
import java.util.List;

/**
 * author: lesya
 */
public class PServerLoginProviderImpl extends PServerLoginProvider {
  private static final Logger LOG = Logger.getInstance(PServerLoginProviderImpl.class);

  @Override
  @Nullable
  public String getScrambledPasswordForCvsRoot(String cvsRoot) {
    return getPassword(cvsRoot);
  }

  @Nullable
  private static String requestForPassword(String cvsRoot) {
    final String password = Messages.showPasswordDialog(CvsBundle.message("prompt.text.enter.password.for.cvs.root", cvsRoot), CvsBundle.message("prompt.title.enter.password.for.cvs.root"));
    return password != null ? PServerPasswordScrambler.getInstance().scramble(password) : null;
  }

  @Override
  public CvsLoginWorker getLoginWorker(final Project project, final PServerCvsSettings pServerCvsSettings) {
    return new PServerLoginWorker(project, pServerCvsSettings);
  }

  private static class PServerLoginWorker extends CvsLoginWorkerImpl<PServerCvsSettings> {
    private PServerLoginWorker(final Project project, final PServerCvsSettings settings) {
      super(project, settings);
    }

    @Override
    protected void silentLoginImpl(final boolean forceCheck) throws AuthenticationException {
      final String cvsRoot = mySettings.getCvsRootAsString();
      final String stored = getPassword(cvsRoot);
      if (stored == null) throw new SolveableAuthenticationException(null);
      if (forceCheck) {
        tryConnection();
      }
    }

    private void tryConnection() throws AuthenticationException {
      final IConnection connection = mySettings.createConnection(new ReadWriteStatistics());
      try {
        connection.open(new StreamLogger());
        mySettings.setOffline(false);
      }
      catch (UnknownUserException e) {
        throw new SolveableAuthenticationException(e.getMessage(), e);
      }
      finally {
        try {
          connection.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }

    @Override
    public boolean promptForPassword() {
      final String cvsRoot = mySettings.getCvsRootAsString();
      final String password = requestForPassword(cvsRoot);
      if (password == null) return false;
      try {
        removeAllPasswordsForThisCvsRootFromPasswordFile(cvsRoot);
        storePassword(cvsRoot, password);
      }
      catch (IOException e) {
        showConnectionErrorMessage(myProject, CvsBundle.message("error.message.cannot.store.password", e.getLocalizedMessage()));
        return false;
      }
      mySettings.storePassword(password);
      return true;
    }

    @Override
    protected void clearOldCredentials() {
      mySettings.releasePassword();
    }
  }

  // TODO do release password ! when opening a connection and there's a problem with authorization

  private static void removeAllPasswordsForThisCvsRootFromPasswordFile(String cvsRoot) throws IOException {
    final File passFile = getPassFile();
    if (!passFile.isFile()) {
      return;
    }
    final List<String> lines = CvsFileUtil.readLinesFrom(passFile, cvsRoot);
    try {
      CvsFileUtil.storeLines(lines, passFile);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void storePassword(String stringConfiguration, String scrambledPassword) throws IOException {
    final File passFile = getPassFile();
    FileUtil.createIfDoesntExist(passFile);
    final List<String> lines = CvsFileUtil.readLinesFrom(passFile);
    lines.add(stringConfiguration + " " + scrambledPassword);
    CvsFileUtil.storeLines(lines, passFile);
  }

  @Nullable
  private static String getPassword(String config) {
    final File passFile = getPassFile();
    try {
      final BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(passFile), CvsApplicationLevelConfiguration.getCharset()));
      try {
        return findPasswordIn(reader, config);
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      return null;
    }
  }

  private static File getPassFile() {
    return CvsApplicationLevelConfiguration.getInstance().getPassFile();
  }

  @Nullable
  private static String findPasswordIn(BufferedReader reader, String config) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      final int position = line.indexOf(config);
      if (position == -1) {
        continue;
      }
      final String result = line.substring(position + config.length());
      if (result.isEmpty()) {
        continue;
      }
      return result.substring(1);
    }
    return null;
  }
}
