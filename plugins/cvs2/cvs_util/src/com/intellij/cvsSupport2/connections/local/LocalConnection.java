package com.intellij.cvsSupport2.connections.local;

import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

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
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(myLocalSettings.PATH_TO_CVS_CLIENT);
    StringTokenizer st = new StringTokenizer(myLocalSettings.SERVER_COMMAND.trim());
     while (st.hasMoreTokens()) {
        commandLine.addParameter(st.nextToken());
     }
    
    execute(commandLine);
  }
}
