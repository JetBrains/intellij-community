package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.InputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.CvsBundle;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.IOException;
import java.util.StringTokenizer;

/**
 * author: lesya
 */

public class ExtConnection extends ConnectionOnProcess {

  @NonNls public static final String DEFAULT_RSH = "ssh";

  public static final int DEFAULT_PORT = 9999;

  private final String myHost;
  private final String myUserName;
  private final ExtConfiguration myConfiguration;
  
  

  public ExtConnection(String host, String user, String repository, ExtConfiguration configuration, ErrorRegistry errorRegistry) {
    super(repository, errorRegistry);
    myHost = host;
    myUserName = user;
    myConfiguration = configuration;
  }


  public void open() throws AuthenticationException {
    try {
      //noinspection HardCodedStringLiteral
      open(new String[]{"cvs", "server"}, null, null);
    }
    catch (IOException e) {
      throw new AuthenticationException(CvsBundle.message("error.message.cannot.establish.external.connection"), e);
    }
  }

  private void open(String[] commands, String expectedResult, ICvsCommandStopper stopper)
    throws AuthenticationException, IOException {
    if (isOpen()) throw new RuntimeException(CvsBundle.message("error.message.connection.already.open"));

    GeneralCommandLine command = createRshCommand(myHost, myUserName, myConfiguration);

    for (String command1 : commands) {
      command.addParameter(command1);
    }

    execute(command);

    if (expectedResult != null) {
      check(stopper, expectedResult);
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void check(ICvsCommandStopper stopper, String expectedResult) throws IOException, AuthenticationException {
    InputStreamWrapper streamWrapper = new InputStreamWrapper(myInputStream, stopper, new ReadWriteStatistics());
    try {
      int i;
      StringBuffer buffer = new StringBuffer();
      while (true) {
        i = streamWrapper.read();
        if (i == -1 || i == '\n' || i == ' ' || i == '\r') break;
        buffer.append((char)i);
      }
      String read = buffer.toString().trim();
      if (!expectedResult.equals(read)) {
        if (read.startsWith(myUserName + "@" + myHost)) {
          throw new AuthenticationException(CvsBundle.message("exception.text.ext.server.rejected.access"), null);
        }
        else {
          if (myErrorText.length() > 0) {
            throw new AuthenticationException(myErrorText.toString(), null);
          }
          else {
            throw new AuthenticationException(CvsBundle.message("exception.text.ext.cannot.establish.external.connection"), null);
          }
        }
      }
    }
    finally {
      streamWrapper.close();
    }
  }

  private static GeneralCommandLine createRshCommand(String host, String userName, ExtConfiguration config) {
    GeneralCommandLine command = new GeneralCommandLine();
    command.setExePath(config.CVS_RSH);
    command.addParameter(host);
    command.addParameter("-l");
    command.addParameter(userName);

    if (config.PRIVATE_KEY_FILE.length() > 0) {
      command.addParameter("-i");
      command.addParameter(config.PRIVATE_KEY_FILE);
    }

    if (config.ADDITIONAL_PARAMETERS.length() > 0) {
      StringTokenizer parameters = new StringTokenizer(config.ADDITIONAL_PARAMETERS, " ");
      while (parameters.hasMoreTokens()) command.addParameter(parameters.nextToken());
    }

    return command;
  }

}

