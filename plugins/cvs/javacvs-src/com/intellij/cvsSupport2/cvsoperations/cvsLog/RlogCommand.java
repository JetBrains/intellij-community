/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsLog;

import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.log.LogMessageParser;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

public class RlogCommand extends AbstractCommand {

  private String myModuleName = ".";

  private boolean myHeadersOnly = true;
  private boolean myNoTags = false;
  private String myDateTo;
  private String myDateFrom;
  private boolean mySuppressEmptyHeaders = true;

  private String myBranchName = null;
  private boolean myLogDefaultBranch = false;
  private String[] myRevisions = null;

  // Implemented ============================================================

  @Override
  public final boolean execute(IRequestProcessor requestProcessor,
                               IEventSender eventSender,
                               ICvsListenerRegistry listenerRegistry,
                               IClientEnvironment clientEnvironment,
                               IProgressViewer progressViewer) throws CommandException, AuthenticationException {
    final Requests requests = new Requests(CommandRequest.RLOG, clientEnvironment);
    requests.addArgumentRequest(myHeadersOnly, "-h");
    requests.addArgumentRequest(myNoTags, "-N");
    requests.addArgumentRequest(mySuppressEmptyHeaders, "-S");
    requests.addArgumentRequest(!isRecursive(), "-l");
    requests.addArgumentRequest(getDateFilter(), "-d");

    final String revisionFilter = getRevisionFilter();
    if (revisionFilter != null) {
      requests.addArgumentRequest("-r" + revisionFilter);
    } else if (myBranchName != null) {
      requests.addArgumentRequest("-r" + myBranchName);
    }

    requests.addArgumentRequest(myLogDefaultBranch, "-b");
    requests.addArgumentRequest(myModuleName);

    final ICvsListener parser = new LogMessageParser(eventSender, clientEnvironment.getCvsFileSystem());
    parser.registerListeners(listenerRegistry);
    try {
      return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
    }
    finally {
      parser.unregisterListeners(listenerRegistry);
    }
  }

  public void setMyLogDefaultBranch(boolean logDefaultBranch) {
    myLogDefaultBranch = logDefaultBranch;
  }

  @Nullable
  private String getDateFilter() {
    if (myDateFrom == null && myDateTo == null) {
      return null;
    }

    final StringBuilder result = new StringBuilder();

    if (myDateFrom == null) {
      result.append('<');
      result.append(myDateTo);
    }
    else if (myDateTo == null) {
      result.append('>');
      result.append(myDateFrom);
    }
    else {
      result.append(myDateFrom);
      result.append('<');
      result.append(myDateTo);
    }

    return result.toString();
  }

  @Nullable
  private String getRevisionFilter() {
    if (myRevisions == null || myRevisions.length == 0) {
      return null;
    }
    final StringBuilder result = new StringBuilder();
    boolean comma = false;
    for (String revision : myRevisions) {
      if (comma) result.append(',');
      else comma = true;
      result.append(revision);
    }
    return result.toString();
  }


  @Override
  public final String getCvsCommandLine() {
    //noinspection HardCodedStringLiteral
    final StringBuffer cvsCommandLine = new StringBuffer("rlog ");
    cvsCommandLine.append(getCVSArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  @Override
  public final void resetCvsCommand() {
    super.resetCvsCommand();
    setRecursive(true);
  }

  public void setHeadersOnly(final boolean headersOnly) {
    myHeadersOnly = headersOnly;
  }

  public void setNoTags(final boolean noTags) {
    myNoTags = noTags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String getCVSArguments() {
    final StringBuilder cvsArguments = new StringBuilder();
    if (myHeadersOnly) {
      cvsArguments.append("-h ");
    }

    if (myNoTags) {
      cvsArguments.append("-N ");
    }

    return cvsArguments.toString();
  }

  public void setModuleName(final String moduleName) {
    myModuleName = moduleName;
  }

  // Utils ==================================================================

  @Override
  protected final void addModifiedRequest(FileObject fileObject, Entry entry, Requests requests, IClientEnvironment clientEnvironment) {
    requests.addIsModifiedRequest(fileObject);
  }

  public void setDateFrom(final String dateFrom) {
    myDateFrom = dateFrom;
  }

  public void setDateTo(final String dateTo) {
    myDateTo = dateTo;
  }

  public void setSuppressEmptyHeaders(final boolean suppressEmptyHeaders) {
    mySuppressEmptyHeaders = suppressEmptyHeaders;
  }

  public void setBranchName(final String branchName) {
    myBranchName = branchName;
  }

  public void setRevisions(final String... revisions) {
    myRevisions = revisions;
  }
}
