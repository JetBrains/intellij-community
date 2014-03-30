/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.connections.ConnectionOnProcess;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.InputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.CvsBundle;
import com.intellij.openapi.util.text.StringUtil;
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
    command.addParameters(commands);
    execute(command);

    if (expectedResult != null) {
      check(stopper, expectedResult);
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void check(ICvsCommandStopper stopper, String expectedResult) throws IOException, AuthenticationException {
    InputStreamWrapper streamWrapper = new InputStreamWrapper(myInputStream, stopper, new ReadWriteStatistics());
    try {
      StringBuilder buffer = new StringBuilder();
      while (true) {
        int i = streamWrapper.read();
        if (i == -1 || i == '\n' || i == ' ' || i == '\r') break;
        buffer.append((char)i);
      }
      String read = buffer.toString().trim();
      if (!expectedResult.equals(read)) {
        if (StringUtil.startsWithConcatenation(read, myUserName, "@", myHost)) {
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

    if (!config.PRIVATE_KEY_FILE.isEmpty()) {
      command.addParameter("-i");
      command.addParameter(config.PRIVATE_KEY_FILE);
    }

    if (!config.ADDITIONAL_PARAMETERS.isEmpty()) {
      StringTokenizer parameters = new StringTokenizer(config.ADDITIONAL_PARAMETERS, " ");
      while (parameters.hasMoreTokens()) command.addParameter(parameters.nextToken());
    }

    return command;
  }
}
