/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

public class RtagCommand extends AbstractCommand{
  private final String myTagName;
  private boolean myOverrideExistings = false;

  public RtagCommand(String tagName) {
    myTagName = tagName;
  }

  @Override
  public final boolean execute(IRequestProcessor requestProcessor,
                               IEventSender eventSender,
                               ICvsListenerRegistry listenerRegistry,
                               IClientEnvironment clientEnvironment,
                               IProgressViewer progressViewer) throws CommandException, AuthenticationException {
    final Requests requests = new Requests(CommandRequest.RTAG, clientEnvironment);
    requests.addArgumentRequest(myOverrideExistings, "-F");
    requests.addArgumentRequest(true, myTagName);
    for (AbstractFileObject fileObject : getFileObjects()) {
      String path = fileObject.getPath();
      if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
      requests.addArgumentRequest(path);
    }
    return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
  }

  @Override
  public final String getCvsCommandLine() {
    @NonNls final StringBuffer cvsCommandLine = new StringBuffer("rtag ");
    cvsCommandLine.append(getCVSArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  private String getCVSArguments() {
    @NonNls final StringBuilder cvsArguments = new StringBuilder();
    cvsArguments.append("-F ").append(myTagName);
    return cvsArguments.toString();
  }

  public void setOverrideExistings(boolean overrideExistings) {
    myOverrideExistings = overrideExistings;
  }
}
