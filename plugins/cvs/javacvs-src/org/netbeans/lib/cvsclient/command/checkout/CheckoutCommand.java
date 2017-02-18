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
package org.netbeans.lib.cvsclient.command.checkout;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.ExpandModulesRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Robert Greig
 */
public final class CheckoutCommand extends AbstractCommand implements IUpdatingCommand {

  // Constants ==============================================================

  @NonNls public static final String EXAM_DIR = "server: Updating ";

  // Fields =================================================================

  private final List<String> modules = new ArrayList<>();
  private boolean pruneDirectories;
  private boolean resetStickyOnes;
  private boolean useHeadIfNotFound;
  private String updateByDate;
  private String updateByRevisionOrTag;
  private String alternativeCheckoutDirectory;
  private KeywordSubstitution keywordSubstitution;
  private boolean printToOutput;
  private final ThrowableRunnable<IOCommandException> myAfterCheckout;

  // Setup ==================================================================

  public CheckoutCommand(final ThrowableRunnable<IOCommandException> afterCheckout) {
    myAfterCheckout = afterCheckout;
  }

  // Implemented ============================================================

  /**
   * Execute this command.
   *
   * @param requestProcessor the client services object that provides any necessary
   *                         services to this command, including the ability to actually process
   *                         all the requests
   */
  public boolean execute(IRequestProcessor requestProcessor,
                         IEventSender eventSender,
                         ICvsListenerRegistry listenerRegistry,
                         IClientEnvironment clientEnvironment,
                         IProgressViewer progressViewer) throws CommandException, AuthenticationException {
    final ExpandedModules expandedModules = new ExpandedModules();
    if (!expandModules(expandedModules, listenerRegistry, requestProcessor, clientEnvironment)) {
      return false;
    }

    return checkout(expandedModules, requestProcessor, listenerRegistry, clientEnvironment);
  }

  /**
   * Resets all switches in the command.
   * After calling this method, the command should have no switches defined
   * and should behave defaultly.
   */
  public void resetCvsCommand() {
    super.resetCvsCommand();
    setRecursive(true);
    setResetStickyOnes(false);
    setUseHeadIfNotFound(false);
    setUpdateByDate(null);
    setUpdateByRevisionOrTag(null);
    setKeywordSubstitution(null);
    setPruneDirectories(false);
    setPrintToOutput(false);
  }

  /**
   * This method returns how the command would looklike when typed on the command line.
   * Each command is responsible for constructing this information.
   *
   * @return <command's name> [<parameters>] files/dirs. Example: checkout -p CvsCommand.java
   */
  public String getCvsCommandLine() {
    @NonNls final StringBuffer cvsCommandLine = new StringBuffer("checkout ");
    cvsCommandLine.append(getCvsArguments());
    for (final String module : modules) {
      cvsCommandLine.append(module);
      cvsCommandLine.append(' ');
    }
    return cvsCommandLine.toString();
  }

  // Accessing ==============================================================

  public void addModule(String module) {
    modules.add(module);
  }

  public void clearModules() {
    this.modules.clear();
  }

  private String getAlternativeCheckoutDirectory() {
    return alternativeCheckoutDirectory;
  }

  public void setAlternativeCheckoutDirectory(String alternativeCheckoutDirectory) {
    this.alternativeCheckoutDirectory = alternativeCheckoutDirectory;
  }

  private boolean isPruneDirectories() {
    return pruneDirectories;
  }

  public void setPruneDirectories(boolean pruneDirectories) {
    this.pruneDirectories = pruneDirectories;
  }

  private boolean isResetStickyOnes() {
    return resetStickyOnes;
  }

  public void setResetStickyOnes(boolean resetStickyOnes) {
    this.resetStickyOnes = resetStickyOnes;
  }

  private boolean isUseHeadIfNotFound() {
    return useHeadIfNotFound;
  }

  public void setUseHeadIfNotFound(boolean useHeadIfNotFound) {
    this.useHeadIfNotFound = useHeadIfNotFound;
  }

  private String getUpdateByDate() {
    return updateByDate;
  }

  public void setUpdateByDate(String updateByDate) {
    this.updateByDate = updateByDate;
  }

  private String getUpdateByRevisionOrTag() {
    return updateByRevisionOrTag;
  }

  public void setUpdateByRevisionOrTag(String updateByRevisionOrTag) {
    this.updateByRevisionOrTag = updateByRevisionOrTag;
  }

  private KeywordSubstitution getKeywordSubstitution() {
    return keywordSubstitution;
  }

  public void setKeywordSubstitution(KeywordSubstitution keywordSubstitution) {
    this.keywordSubstitution = keywordSubstitution;
  }

  public void setPrintToOutput(final boolean printToOutput) {
    this.printToOutput = printToOutput;
  }

  public boolean isPrintToOutput() {
    return this.printToOutput;
  }
  // Utils ==================================================================

  private boolean expandModules(ExpandedModules expandedModules,
                                ICvsListenerRegistry listenerRegistry,
                                IRequestProcessor requestProcessor,
                                IClientEnvironment clientEnvironment) throws CommandException, AuthenticationException {
    final Requests requests = new Requests(new ExpandModulesRequest(), clientEnvironment);

    addModuleArguments(requests);
    requests.addLocalPathDirectoryRequest();

    expandedModules.registerListeners(listenerRegistry);
    try {
      return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
    }
    finally {
      expandedModules.unregisterListeners(listenerRegistry);
    }
  }

  private boolean checkout(ExpandedModules expandedModules,
                           IRequestProcessor requestProcessor,
                           ICvsListenerRegistry listenerRegistry,
                           IClientEnvironment clientEnvironment) throws CommandException, AuthenticationException {
    // we first see whether the modules specified actually exist
    // checked out already. If so, we must work something like an update
    // command and send modified files to the server.
    processExistingModules(expandedModules, clientEnvironment);

    final ICvsFiles cvsFiles;
    try {
      if (getFileObjects().size() > 0) {
        cvsFiles = scanFileSystem(clientEnvironment);
      }
      else {
        cvsFiles = null;
      }
    }
    catch (IOException ex) {
      throw new IOCommandException(ex);
    }

    final Requests requests = new Requests(CommandRequest.CHECKOUT, clientEnvironment);
    if (getAlternativeCheckoutDirectory() != null) {
      requests.addArgumentRequest("-d");
      requests.addArgumentRequest(getAlternativeCheckoutDirectory());
    }
    requests.addArgumentRequest(!isRecursive(), "-l");
    requests.addArgumentRequest(isResetStickyOnes(), "-A");
    requests.addArgumentRequest(isUseHeadIfNotFound(), "-f");
    requests.addArgumentRequest(getUpdateByDate(), "-D");
    requests.addArgumentRequest(getUpdateByRevisionOrTag(), "-r");
    requests.addArgumentRequest(getKeywordSubstitution(), "-k");
    requests.addArgumentRequest(isPrintToOutput(), "-p");
    if (cvsFiles != null) {
      addFileRequests(cvsFiles, requests, clientEnvironment);
    }
    addModuleArguments(requests);
    requests.addLocalPathDirectoryRequest();

    final DirectoryPruner directoryPruner;
    if (isPruneDirectories()) {
      directoryPruner = new DirectoryPruner(clientEnvironment);
    }
    else {
      directoryPruner = null;
    }

    if (directoryPruner != null) {
      directoryPruner.registerListeners(listenerRegistry);
    }
    try {
      return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
    }
    finally {
      try {
        if (myAfterCheckout != null) {
          myAfterCheckout.run();
        }
      } finally {
        if (directoryPruner != null) {
          directoryPruner.unregisterListeners(listenerRegistry);
          try {
            directoryPruner.pruneEmptyDirectories();
          }
          catch (IOException ex) {
            throw new IOCommandException(ex);
          }
        }
      }
    }
  }

  private void addModuleArguments(Requests requests) {
    for (final String module : modules) {
      requests.addArgumentRequest(module);
    }
  }

  private void processExistingModules(ExpandedModules expandedModules, IClientEnvironment clientEnvironment) {
    final ICvsFileSystem cvsFileSystem = clientEnvironment.getCvsFileSystem();

    for (final String moduleName : expandedModules.getModules()) {
      if (moduleName.equals(".")) {
        addFileObject(DirectoryObject.getRoot());
        break;
      }

      final File moduleFile = cvsFileSystem.getLocalFileSystem().getFile(moduleName);
      final AbstractFileObject abstractFileObject;
      final DirectoryObject directoryObject;
      if (moduleFile.isFile()) {
        abstractFileObject = cvsFileSystem.getLocalFileSystem().getFileObject(moduleFile);
        directoryObject = abstractFileObject.getParent();
      }
      else {
        directoryObject = cvsFileSystem.getLocalFileSystem().getDirectoryObject(moduleFile);
        abstractFileObject = directoryObject;
      }

      if (clientEnvironment.getAdminReader().hasCvsDirectory(directoryObject, cvsFileSystem)) {
        addFileObject(abstractFileObject);
      }
    }
  }

  private String getCvsArguments() {
    @NonNls final StringBuffer cvsArguments = new StringBuffer();
    if (!isRecursive()) {
      cvsArguments.append("-l ");
    }
    if (isResetStickyOnes()) {
      cvsArguments.append("-A ");
    }
    if (isUseHeadIfNotFound()) {
      cvsArguments.append("-f ");
    }
    if (isPrintToOutput()) {
      cvsArguments.append("-p ");
    }

    if (isPruneDirectories()) {
      cvsArguments.append("-P ");
    }

    if (getKeywordSubstitution() != null) {
      cvsArguments.append("-k");
      cvsArguments.append(getKeywordSubstitution());
      cvsArguments.append(' ');
    }
    if (getUpdateByRevisionOrTag() != null && getUpdateByRevisionOrTag().length() > 0) {
      cvsArguments.append("-r ");
      cvsArguments.append(getUpdateByRevisionOrTag());
      cvsArguments.append(' ');
    }
    if (getUpdateByDate() != null && getUpdateByDate().length() > 0) {
      cvsArguments.append("-D ");
      cvsArguments.append(getUpdateByDate());
      cvsArguments.append(' ');
    }
    return cvsArguments.toString();
  }

  @Override
  public void setUpdateByRevisionOrDate(String revision, String date) {
    setUpdateByRevisionOrTag(revision);
    setUpdateByDate(date);
  }
}
