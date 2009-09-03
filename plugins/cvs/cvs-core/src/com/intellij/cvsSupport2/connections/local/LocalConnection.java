package com.intellij.cvsSupport2.connections.local;

import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.IOException;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class LocalConnection extends ConnectionOnProcess{
  private final LocalSettings myLocalSettings;

  public LocalConnection(String repository, LocalSettings localSettings, ErrorRegistry errorRegistry) {
    super(repository, errorRegistry);
    myLocalSettings = localSettings;
  }

  public void open() throws AuthenticationException {
    if (!myLocalSettings.isCvsClientVerified()) {
      verifyServerCapability();
      myLocalSettings.setCvsClientVerified(true);
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(myLocalSettings.PATH_TO_CVS_CLIENT);
    StringTokenizer st = new StringTokenizer(myLocalSettings.SERVER_COMMAND.trim());
     while (st.hasMoreTokens()) {
        commandLine.addParameter(st.nextToken());
     }
    
    execute(commandLine);
  }

  private void verifyServerCapability() throws AuthenticationException {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(myLocalSettings.PATH_TO_CVS_CLIENT);
    commandLine.addParameter("-v");
    execute(commandLine);
    try {
      StringBuilder responseBuilder = new StringBuilder();
      while(true) {
        int c = myInputStream.read();
        if (c == -1) {
          break;
        }
        responseBuilder.append((char) c);
      }
      String[] lines = responseBuilder.toString().split("\n");
      for (String line : lines) {
        // check that the first non-empty line does not end with (client)
        if (line.trim().endsWith("(client)")) {
          throw new AuthenticationException("CVS client does not support server mode operation", null);
        }
        if (line.trim().length() > 0) {
          break;
        }
      }
    }
    catch (IOException e) {
      throw new AuthenticationException("Can't read CVS version", e);
    }
    closeInternal();
  }
}
