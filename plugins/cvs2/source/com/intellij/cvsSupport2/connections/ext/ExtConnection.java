package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.InputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * author: lesya
 */

public class ExtConnection extends ConnectionOnProcess {

  public static final String DEFAULT_RSH = "ssh";

  public static final int DEFAULT_PORT = 9999;

  private final String myHost;
  private final String myUserName;
  private final ExtConfiguration myConfiguration;

  public ExtConnection(String host, String user, String repository, ExtConfiguration configuration,
                       ErrorRegistry errorRegistry, ModalityContext executor) {
    super(repository, errorRegistry, executor);
    myHost = host;
    myUserName = user;
    myConfiguration = configuration;
  }


  public void open() throws AuthenticationException {
    try {
      open(new String[]{"cvs", "server"}, null, null);
    }
    catch (IOException e) {
      throw new AuthenticationException("Cannot establish external connection", e);
    }
  }

  private void open(String[] commands, String expectedResult, ICvsCommandStopper stopper)
    throws AuthenticationException, IOException {
    if (isOpen()) throw new RuntimeException("Connection already open");

    ArrayList command = createRshCommand(myHost, myUserName, myConfiguration);

    for (int i = 0; i < commands.length; i++) {
      command.add(commands[i]);
    }

    execute(command);

    if (expectedResult != null) {
      check(stopper, expectedResult);
    }
  }

  private void check(ICvsCommandStopper stopper, String expectedResult) throws IOException, AuthenticationException {
    InputStreamWrapper streamWrapper = new InputStreamWrapper(myInputStream, stopper, new ReadWriteStatistics());
    try {
      int i = 0;
      StringBuffer buffer = new StringBuffer();
      while (i != -1) {
        i = streamWrapper.read();
        if (i == -1 || i == '\n' || i == ' ' || i == '\r') break;
        buffer.append((char)i);
      }
      String read = buffer.toString().trim();
      if (!expectedResult.equals(read)) {
        if (read.startsWith(myUserName + "@" + myHost)) {
          throw new AuthenticationException("Server rejected access", null);
        }
        else {
          InputStream errorStream = myProcess.getErrorStream();
          if (errorStream.available() > 0) {
            throw new AuthenticationException(readFrom(errorStream), null);
          }
          else {
            throw new AuthenticationException("Cannot establish external connection", null);
          }
        }
      }
    }
    finally {
      streamWrapper.close();
    }
  }

  private static ArrayList createRshCommand(String host, String userName, ExtConfiguration config) {
    ArrayList command = new ArrayList();
    command.add(config.CVS_RSH);
    command.add(host);
    command.add("-l");
    command.add(userName);

    if (config.PRIVATE_KEY_FILE.length() > 0) {
      command.add("-i");
      command.add(config.PRIVATE_KEY_FILE);
    }

    if (config.ADDITIONAL_PARAMETERS.length() > 0) {
      StringTokenizer parameters = new StringTokenizer(config.ADDITIONAL_PARAMETERS, " ");
      while (parameters.hasMoreTokens()) command.add(parameters.nextToken());
    }

    return command;
  }

}

