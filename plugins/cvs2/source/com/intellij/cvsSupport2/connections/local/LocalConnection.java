package com.intellij.cvsSupport2.connections.local;

import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.util.Arrays;

/**
 * author: lesya
 */
public class LocalConnection extends ConnectionOnProcess{
  private final LocalSettings myLocalSettings;
  public LocalConnection(String repository, LocalSettings localSettings, ErrorRegistry errorRegistry, ModalityContext executor) {
    super(repository, errorRegistry, executor);
    myLocalSettings = localSettings;
  }

  public void open() throws AuthenticationException {
    execute(Arrays.asList(new String[]{myLocalSettings.PATH_TO_CVS_CLIENT + " " + myLocalSettings.SERVER_COMMAND.trim()}));
  }
}
