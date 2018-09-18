/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command.remove;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;

/**
 * @author Thomas Singer
 */
public final class RemoveCommand extends AbstractCommand {

  // Fields =================================================================

  private boolean deleteBeforeRemove;
  private boolean ignoreLocallyExistingFiles;

  // Setup ==================================================================

  public RemoveCommand() {
  }

  // Implemented ============================================================

  @Override
  public boolean execute(IRequestProcessor requestProcessor,
                         IEventSender eventSender,
                         ICvsListenerRegistry listenerRegistry,
                         IClientEnvironment clientEnvironment,
                         IProgressViewer progressViewer) throws CommandException, AuthenticationException {
    final ICvsFiles cvsFiles;
    try {
      cvsFiles = scanFileSystem(clientEnvironment);
    }
    catch (IOException ex) {
      throw new IOCommandException(ex);
    }

    final Requests requests = new Requests(CommandRequest.REMOVE, clientEnvironment);
    addFileRequests(cvsFiles, requests, clientEnvironment);
    requests.addLocalPathDirectoryRequest();
    addArgumentRequests(requests);

    final ICvsListener parser = new RemoveParser(eventSender, clientEnvironment.getCvsFileSystem());
    parser.registerListeners(listenerRegistry);
    try {
      return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
    }
    finally {
      parser.unregisterListeners(listenerRegistry);
    }
  }

  @Override
  protected void addRequestForFile(FileObject fileObject,
                                   Entry entry,
                                   boolean fileExists,
                                   Requests requests,
                                   IClientEnvironment clientEnvironment) {
    if (isDeleteBeforeRemove()) {
      try {
        clientEnvironment.getLocalFileWriter()
          .removeLocalFile(fileObject, clientEnvironment.getCvsFileSystem(), clientEnvironment.getFileReadOnlyHandler());
      }
      catch (IOException ex) {
        BugLog.getInstance().showException(ex);
      }
      fileExists = false;
    }
    if (isIgnoreLocallyExistingFiles()) {
      fileExists = false;
    }
    super.addRequestForFile(fileObject, entry, fileExists, requests, clientEnvironment);
  }

  @Override
  public String getCvsCommandLine() {
    @NonNls final StringBuffer cvsCommandLine = new StringBuffer("remove ");
    cvsCommandLine.append(getCvsArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  @Override
  public void resetCvsCommand() {
    super.resetCvsCommand();
    setRecursive(false);
    setDeleteBeforeRemove(false);
    setIgnoreLocallyExistingFiles(false);
  }

  // Accessing ==============================================================

  /**
   * Returns true if the local files will be deleted automatically.
   */
  private boolean isDeleteBeforeRemove() {
    return deleteBeforeRemove;
  }

  /**
   * Sets whether the local files will be deleted before.
   */
  public void setDeleteBeforeRemove(boolean deleteBeforeRemove) {
    this.deleteBeforeRemove = deleteBeforeRemove;
  }

  /**
   * Returns true to indicate that locally existing files are treated as they
   * would not exist.
   * This is a extension to the standard cvs-behaviour!
   */
  private boolean isIgnoreLocallyExistingFiles() {
    return ignoreLocallyExistingFiles;
  }

  /**
   * Sets whether locally existing files will be treated as they were deleted
   * before.
   * This is a extension to the standard cvs-behaviour!
   */
  public void setIgnoreLocallyExistingFiles(boolean ignoreLocallyExistingFiles) {
    this.ignoreLocallyExistingFiles = ignoreLocallyExistingFiles;
  }

  // Utils ==================================================================

  private String getCvsArguments() {
    @NonNls final StringBuilder toReturn = new StringBuilder();
    if (!isRecursive()) {
      toReturn.append("-l ");
    }
    if (isDeleteBeforeRemove()) {
      toReturn.append("-f ");
    }
    return toReturn.toString();
  }
}
