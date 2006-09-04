package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.cvsSupport2.util.CvsFileUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.PasswordPromptDialog;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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

  private static String getMessageFrom(AuthenticationException e) {
    Throwable underlyingThrowable = e.getCause();
    if (underlyingThrowable == null) {
      return e.getLocalizedMessage() == null ? e.getMessage() : e.getLocalizedMessage();
    }

    return underlyingThrowable.getLocalizedMessage() == null ? underlyingThrowable.getMessage()
        : underlyingThrowable.getLocalizedMessage();
  }

  public boolean login(PServerCvsSettings settings, ModalityContext executor) {
    String cvsRoot = settings.getCvsRootAsString();
    String stored = getPassword(cvsRoot);
    if (stored != null) {
      IConnection connection = settings.createConnection(new ReadWriteStatistics());
      try {
        connection.open(new StreamLogger());
        return true;
      } catch (AuthenticationException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SocketTimeoutException){
          showErrorMessage(CvsBundle.message("error.message.timeout.error"), CvsBundle.message("error.dialog.title.timeout.error"));
          return false;
        } else if (cause instanceof UnknownHostException){
          showErrorMessage(CvsBundle.message("error.message.unknown.host", settings.HOST),
                                     CvsBundle.message("error.title.inknown.host"));
          return false;
        } else {
        showErrorMessage(getMessageFrom(e), CvsBundle.message("error.title.authorization.error"));
        settings.releasePassword();
        return relogin(settings, executor);
        }
      } finally {
        try {
          connection.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
    String password = requestForPassword(cvsRoot);
    if (password == null) return false;
    try {
      storePassword(cvsRoot, password);
    } catch (IOException e) {
      Messages.showMessageDialog(CvsBundle.message("error.message.cannot.store.password", e.getLocalizedMessage()),
                                 CvsBundle.message("error.title.storing.cvs.password"), Messages.getErrorIcon());
      return false;
    }
    settings.storePassword(password);
    return login(settings, executor);
  }

  private static void showErrorMessage(final String message, final String title) {
    Runnable showErrorAction = new Runnable(){
          public void run() {
            Messages.showErrorDialog(message, title);
          }
        };
    if (ApplicationManager.getApplication().isDispatchThread())
      showErrorAction.run();
    else {
      ApplicationManager.getApplication().invokeLater(showErrorAction);
    }
  }

  public boolean relogin(PServerCvsSettings settings, ModalityContext executor) {
    String cvsRoot = settings.getCvsRootAsString();
    String password = requestForPassword(cvsRoot);
    if (password == null) return false;
    removeAllPasswordsForThisCvsRootFromPasswordFile(cvsRoot);
    try {
      storePassword(cvsRoot, password);
    } catch (IOException e) {
      Messages.showMessageDialog(e.getLocalizedMessage(), CvsBundle.message("error.title.cannot.store.password"), Messages.getErrorIcon());
      return false;
    }
    settings.storePassword(password);
    return login(settings, executor);
  }

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
    if (!passFile.exists()) passFile.createNewFile();
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
