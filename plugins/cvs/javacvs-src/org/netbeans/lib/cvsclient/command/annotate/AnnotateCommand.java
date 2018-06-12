/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

package org.netbeans.lib.cvsclient.command.annotate;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * The annotate command shows all lines of the file and annotates each line with cvs-related info.
 * @author  Milos Kleint
 */
public final class AnnotateCommand extends AbstractCommand {

  // Fields =================================================================

  private boolean useHeadIfNotFound;
  private String date;
  private String revisionOrTag;
  private boolean annotateBinary;

  // Setup ==================================================================

  public AnnotateCommand() {
  }

  // Implemented ============================================================

  public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                            AuthenticationException {
    final ICvsFiles cvsFiles;
    try {
      cvsFiles = scanFileSystem(clientEnvironment);
    }
    catch (IOException ex) {
      throw new IOCommandException(ex);
    }

    final Requests requests = new Requests(CommandRequest.ANNOTATE, clientEnvironment);
    requests.addArgumentRequest(isUseHeadIfNotFound(), "-f");
    requests.addArgumentRequest(getDate(), "-D");
    requests.addArgumentRequest(getRevisionOrTag(), "-r");
    requests.addArgumentRequest(isAnnotateBinary(), "-F");
    addFileRequests(cvsFiles, requests, clientEnvironment);
    requests.addLocalPathDirectoryRequest();
    addArgumentRequests(requests);

    final ICvsListener parser = new AnnotateMessageParser(eventManager, clientEnvironment.getCvsFileSystem());
    parser.registerListeners(listenerRegistry);
    try {
      return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
    }
    finally {
      parser.unregisterListeners(listenerRegistry);
    }
  }

  public void resetCvsCommand() {
    super.resetCvsCommand();
    setRecursive(true);
    setDate(null);
    setAnnotateByRevisionOrTag(null);
    setUseHeadIfNotFound(false);
    setAnnotateBinary(false);
  }

  public String getCvsCommandLine() {
    @NonNls final StringBuffer cvsCommandLine = new StringBuffer("annotate ");
    cvsCommandLine.append(getCvsArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  // Accessing ==============================================================

  private String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  private String getRevisionOrTag() {
    return revisionOrTag;
  }

  public void setAnnotateByRevisionOrTag(String annotateByRevision) {
    this.revisionOrTag = annotateByRevision;
  }

  private boolean isUseHeadIfNotFound() {
    return useHeadIfNotFound;
  }

  private void setUseHeadIfNotFound(boolean useHeadIfNotFound) {
    this.useHeadIfNotFound = useHeadIfNotFound;
  }

  private boolean isAnnotateBinary() {
    return annotateBinary;
  }

  public void setAnnotateBinary(boolean annotateBinary) {
    this.annotateBinary = annotateBinary;
  }

  // Utils ==================================================================

  private String getCvsArguments() {
    @NonNls final StringBuilder cvsArguments = new StringBuilder();
    if (!isRecursive()) {
      cvsArguments.append("-l ");
    }
    if (getRevisionOrTag() != null) {
      cvsArguments.append("-r ");
      cvsArguments.append(getRevisionOrTag());
      cvsArguments.append(" ");
    }
    if (getDate() != null) {
      cvsArguments.append("-D ");
      cvsArguments.append(getDate());
      cvsArguments.append(" ");
    }
    if (isUseHeadIfNotFound()) {
      cvsArguments.append("-f ");
    }
    if (isAnnotateBinary()) {
      cvsArguments.append("-F ");
    }
    return cvsArguments.toString();
  }
}
