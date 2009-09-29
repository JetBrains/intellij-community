package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorkerImpl;
import com.intellij.cvsSupport2.connections.ssh.SolveableAuthenticationException;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.cvsSupport2.util.CvsFileUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.PasswordPromptDialog;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;
import org.netbeans.lib.cvsclient.connection.UnknownUserException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class PServerLoginProviderImpl extends PServerLoginProvider {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.pserver.PServerLoginProviderImpl");

  @Nullable
  public String getScrambledPasswordForCvsRoot(String cvsroot) {
    return getPassword(cvsroot);
  }

  @Nullable
  private static String requestForPassword(String cvsroot) {
    PasswordPromptDialog passwordDialog = new PasswordPromptDialog(CvsBundle.message("propmt.text.enter.password.for.cvs.root", cvsroot),
                                                                   CvsBundle.message("propmt.title.enter.password.for.cvs.root"), null);
    passwordDialog.show();
    if (!passwordDialog.isOK()) return null;
    return PServerPasswordScrambler.getInstance().scramble(passwordDialog.getPassword());
  }

  public CvsLoginWorker getLoginWorker(final ModalityContext executor, final Project project, final PServerCvsSettings pServerCvsSettings) {
    return new MyLoginWorker(project, pServerCvsSettings, executor);
  }

  private static class MyLoginWorker extends CvsLoginWorkerImpl<PServerCvsSettings> {
    private MyLoginWorker(final Project project, final PServerCvsSettings settings, final ModalityContext executor) {
      super(project, settings, executor);
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
      IConnection connection = mySettings.createConnection(new ReadWriteStatistics());
      try {
        connection.open(new StreamLogger());
        mySettings.setOffline(false);
      } catch (AuthenticationException e) {
        if (e instanceof UnknownUserException) {
          throw new SolveableAuthenticationException(e.getMessage(), e);
        } else {
          throw e;
        }
      } finally {
        try {
          connection.close();
        }
        catch (IOException e) {
          // ignore
        }
      }
    }

    @Override
    public boolean promptForPassword() {
      final String cvsRoot = mySettings.getCvsRootAsString();
      final String password = requestForPassword(cvsRoot);
      if (password == null) return false;
      removeAllPasswordsForThisCvsRootFromPasswordFile(cvsRoot);
      try {
        storePassword(cvsRoot, password);
      } catch (IOException e) {
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

  private static ArrayList<String> readConfigurationNotMatchedWith(String cvsRoot, File passFile) {
    FileInputStream input;
    try {
      input = new FileInputStream(passFile);
    } catch (FileNotFoundException e) {
      return new ArrayList<String>();
    }

    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    ArrayList<String> result = new ArrayList<String>();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.indexOf(cvsRoot) == -1) result.add(line);
      }
    } catch (IOException ex) {
      // ignore
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        // ignore
      }
    }
    return result;
  }

  private static void removeAllPasswordsForThisCvsRootFromPasswordFile(String cvsRoot) {
    File passFile = getPassFile();
    if (passFile == null) return;
    if (!passFile.isFile()) return;

    ArrayList<String> lines = readConfigurationNotMatchedWith(cvsRoot, passFile);

    try {
      CvsFileUtil.storeLines(lines, passFile);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void storePassword(String stringConfiguration, String scrambledPassword) throws IOException {
    File passFile = getPassFile();
    FileUtil.createIfDoesntExist(passFile);
    List<String> lines = CvsFileUtil.readLinesFrom(passFile);
    lines.add(stringConfiguration + " " + scrambledPassword);
    CvsFileUtil.storeLines(lines, passFile);
  }

  @Nullable
  private static String getPassword(String config) {
    File passFile = getPassFile();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(passFile)));
      try {
        return findPasswordIn(reader, config);
      } finally {
        reader.close();
      }
    } catch (IOException e) {
      return null;
    }
  }

  private static File getPassFile() {
    return new File(CvsApplicationLevelConfiguration.getInstance().getPathToPassFile());
  }

  @Nullable
  private static String findPasswordIn(BufferedReader reader, String config) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      int position = line.indexOf(config);
      if (position != -1) {
        String result = line.substring(position + config.length());
        return result.substring(1);
      }
    }
    return null;
  }

}
