package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.cvsSupport2.ui.PasswordPromptDialog;
import com.intellij.cvsSupport2.util.CvsFileUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerPasswordScrambler;

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

  public String getScrambledPasswordForCvsRoot(String cvsroot) {
    return loadOrRequestPassword(cvsroot);
  }

  private String requestForPassword(String cvsroot) {
    PasswordPromptDialog passwordDialog = new PasswordPromptDialog("Enter password for " + cvsroot);
    passwordDialog.show();
    if (!passwordDialog.isOK()) return null;
    return PServerPasswordScrambler.getInstance().scramble(passwordDialog.getPassword());
  }

  private String getMessageFrom(AuthenticationException e) {
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
      IConnection connection = settings.createConnection(new ReadWriteStatistics(), executor);
      try {
        connection.open(new StreamLogger());
        return true;
      } catch (AuthenticationException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SocketTimeoutException){
          showErrorMessage("Timeout error.", "Timeout Error");
          return false;
        } else if (cause instanceof UnknownHostException){
          showErrorMessage("Unknown host: " + settings.HOST,
                                     "Unknown Host");
          return false;
        } else {
        showErrorMessage(getMessageFrom(e), "Authorization Error");
        settings.releasePassword();
        return relogin(settings, executor);
        }
      } finally {
        try {
          connection.close();
        } catch (IOException e) {

        }
      }
    }
    String password = requestForPassword(cvsRoot);
    if (password == null) return false;
    try {
      storePassword(cvsRoot, password);
    } catch (IOException e) {
      Messages.showMessageDialog("Cannot Store Password: " + e.getLocalizedMessage(),
                                 "Storing CVS Password", Messages.getErrorIcon());
      return false;
    }
    settings.storePassword(password);
    return login(settings, executor);
  }

  private void showErrorMessage(final String message, final String title) {
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
      Messages.showMessageDialog(e.getLocalizedMessage(), "Cannot Store Password", Messages.getErrorIcon());
      return false;
    }
    settings.storePassword(password);
    return login(settings, executor);
  }

  private ArrayList readConfigurationNotMatchedWith(String cvsRoot, File passFile) {
    FileInputStream input;
    try {
      input = new FileInputStream(passFile);
    } catch (FileNotFoundException e) {
      return new ArrayList();
    }

    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    ArrayList result = new ArrayList();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.indexOf(cvsRoot) == -1) result.add(line);
      }
    } catch (IOException ex) {

    } finally {
      try {
        reader.close();
      } catch (IOException e) {
      }
    }
    return result;
  }

  private void removeAllPasswordsForThisCvsRootFromPasswordFile(String cvsRoot) {
    File passFile = getPassFile();
    if (passFile == null) return;
    if (!passFile.isFile()) return;

    ArrayList lines = readConfigurationNotMatchedWith(cvsRoot, passFile);

    try {
      CvsFileUtil.storeLines(lines, passFile);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private String loadOrRequestPassword(String stringConfiguration) {
    return getPassword(stringConfiguration);
  }

  private void storePassword(String stringConfiguration, String scrambledPassword) throws IOException {
    File passFile = getPassFile();
    if (!passFile.exists()) passFile.createNewFile();
    List lines = CvsFileUtil.readLinesFrom(passFile);
    lines.add(stringConfiguration + " " + scrambledPassword);
    CvsFileUtil.storeLines(lines, passFile);
  }

  private String getPassword(String config) {
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

  private File getPassFile() {
    return new File(CvsApplicationLevelConfiguration.getInstance().getPathToPassFile());
  }

  private String findPasswordIn(BufferedReader reader, String config) throws IOException {
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
