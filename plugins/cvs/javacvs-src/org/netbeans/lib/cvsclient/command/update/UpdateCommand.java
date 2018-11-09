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
package org.netbeans.lib.cvsclient.command.update;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.DualListener;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.progress.receiving.FileInfoAndDirectoryResponseProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * The Update command. Updates files that have previously been checked out
 * from the repository with the checkout command. Modified files are not
 * overwritten.
 * @author  Robert Greig
 */
public final class UpdateCommand extends AbstractCommand
        implements IUpdatingCommand {

	// Fields =================================================================

	private boolean buildDirectories;
	private boolean cleanCopy;
	private boolean pruneDirectories;
	private boolean resetStickyOnes;
	private boolean useHeadIfNotFound;
	private String updateByDate;
	private String updateByRevision;
	private KeywordSubstitution keywordSubst;
	private String mergeRevision1;
	private String mergeRevision2;

	// Setup ==================================================================

	public UpdateCommand() {
	}

	// Implemented ============================================================

	/**
	 * Execute the command.
	 * @param requestProcessor the client services object that provides any necessary
	 *               services to this command, including the ability to actually
	 *               process all the requests
	 */
	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventSender, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer)
          throws CommandException, AuthenticationException {
		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(CommandRequest.UPDATE, clientEnvironment);
		requests.addArgumentRequest(isBuildDirectories(), "-d");
		requests.addArgumentRequest(isCleanCopy(), "-C");
		requests.addArgumentRequest(isResetStickyOnes(), "-A");
		requests.addArgumentRequest(isUseHeadIfNotFound(), "-f");
		requests.addArgumentRequest(getUpdateByDate(), "-D");
		requests.addArgumentRequest(getUpdateByRevision(), "-r");
		requests.addArgumentRequests(getMergeRevision1(), "-j");
		requests.addArgumentRequests(getMergeRevision2(), "-j");
		requests.addArgumentRequest(getKeywordSubst(), "-k");
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		DirectoryPruner directoryPruner = null;
		if (isPruneDirectories()) {
			directoryPruner = new DirectoryPruner(clientEnvironment);
			directoryPruner.registerListeners(listenerRegistry);
		}

		final IRequestsProgressHandler requestsProgressHandler = new FileStateRequestsProgressHandler(new RangeProgressViewer(progressViewer, 0.0, 0.5), cvsFiles);
		final ICvsListener responseProgressViewer = new FileInfoAndDirectoryResponseProgressHandler(new RangeProgressViewer(progressViewer, 0.5, 1.0), cvsFiles);

		final ICvsListener updateMessageParser = new UpdateMessageParser(eventSender, clientEnvironment.getCvsFileSystem());
		final ICvsListener listener = new DualListener(updateMessageParser, responseProgressViewer);
		listener.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, requestsProgressHandler);
		}
		finally {
			listener.unregisterListeners(listenerRegistry);

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

	/**
	 * This method returns how the command would looklike when typed on the
	 * command line.
	 * Each command is responsible for constructing this information.
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("update ");
		cvsCommandLine.append(getCvsArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	/**
	 * Resets all switches in the command.
	 * After calling this method, the command should have no switches defined
	 * and should behave defaultly.
	 */
	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
		setCleanCopy(false);
		setBuildDirectories(false);
		setPruneDirectories(false);
		setResetStickyOnes(false);
		setUseHeadIfNotFound(false);
		setUpdateByDate(null);
		setUpdateByRevisionOrTag(null);
		setKeywordSubst(null);
		setMergeRevision1(null);
		setMergeRevision2(null);
	}

	@Override
        protected void addModifiedRequest(FileObject fileObject, Entry entry, Requests requests, IClientEnvironment clientEnvironment) {
		if (isCleanCopy()) {
			if (!getGlobalOptions().isDoNoChanges()) {
				final String newFileName = ".#" + fileObject.getName() + '.' + entry.getRevision();

				try {
					clientEnvironment.getLocalFileWriter().renameLocalFile(fileObject, clientEnvironment.getCvsFileSystem(), newFileName);
				}
				catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			return;
		}

		super.addModifiedRequest(fileObject, entry, requests, clientEnvironment);
	}

	// Accessing ==============================================================

	public void setBuildDirectories(boolean buildDirectories) {
		this.buildDirectories = buildDirectories;
	}

	private boolean isBuildDirectories() {
		return buildDirectories;
	}

	public void setCleanCopy(boolean cleanCopy) {
		this.cleanCopy = cleanCopy;
	}

	private boolean isCleanCopy() {
		return cleanCopy;
	}

	public void setPruneDirectories(boolean pruneDirectories) {
		this.pruneDirectories = pruneDirectories;
	}

	private boolean isPruneDirectories() {
		return pruneDirectories;
	}

	private boolean isResetStickyOnes() {
		return resetStickyOnes;
	}

	@Override
        public void setResetStickyOnes(boolean resetStickyOnes) {
		this.resetStickyOnes = resetStickyOnes;
	}

	private boolean isUseHeadIfNotFound() {
		return useHeadIfNotFound;
	}

	@Override
        public void setUseHeadIfNotFound(boolean useHeadIfNotFound) {
		this.useHeadIfNotFound = useHeadIfNotFound;
	}

	private String getUpdateByDate() {
		return updateByDate;
	}

	@Override
        public void setUpdateByDate(String updateByDate) {
		this.updateByDate = getTrimmedString(updateByDate);
	}

	public String getUpdateByRevision() {
		return updateByRevision;
	}

	@Override
        public void setUpdateByRevisionOrTag(String updateByRevision) {
		this.updateByRevision = getTrimmedString(updateByRevision);
	}

	private KeywordSubstitution getKeywordSubst() {
		return keywordSubst;
	}

	public void setKeywordSubst(KeywordSubstitution keywordSubst) {
		this.keywordSubst = keywordSubst;
	}

	private String getMergeRevision1() {
		return mergeRevision1;
	}

	public void setMergeRevision1(String mergeRevision1) {
		this.mergeRevision1 = getTrimmedString(mergeRevision1);
	}

	private String getMergeRevision2() {
		return mergeRevision2;
	}

	public void setMergeRevision2(String mergeRevision2) {
		this.mergeRevision2 = getTrimmedString(mergeRevision2);
	}

	// Utils ==================================================================

	/**
	 * Returns the arguments of the command in the command-line style.
	 * Similar to getCVSCommand() however without the files and command's name
	 */
	private String getCvsArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		if (isCleanCopy()) {
			cvsArguments.append("-C ");
		}
		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}
		if (isBuildDirectories()) {
			cvsArguments.append("-d ");
		}
		if (isPruneDirectories()) {
			cvsArguments.append("-P ");
		}
		if (isResetStickyOnes()) {
			cvsArguments.append("-A ");
		}
		if (isUseHeadIfNotFound()) {
			cvsArguments.append("-f ");
		}
		if (getKeywordSubst() != null) {
			cvsArguments.append("-k");
			cvsArguments.append(getKeywordSubst());
			cvsArguments.append(' ');
		}
		if (getUpdateByRevision() != null) {
			cvsArguments.append("-r ");
			cvsArguments.append(getUpdateByRevision());
			cvsArguments.append(' ');
		}
		if (getUpdateByDate() != null) {
			cvsArguments.append("-D ");
			cvsArguments.append(getUpdateByDate());
			cvsArguments.append(' ');
		}
		if (getMergeRevision1() != null) {
			cvsArguments.append("-j ");
			cvsArguments.append(getMergeRevision1());
			cvsArguments.append(' ');

			if (getMergeRevision2() != null) {
				cvsArguments.append("-j ");
				cvsArguments.append(getMergeRevision2());
				cvsArguments.append(' ');
			}
		}
		return cvsArguments.toString();
	}

	@Override
        public void setUpdateByRevisionOrDate(String revision, final String date) {
		setUpdateByRevisionOrTag(revision);
		setUpdateByDate(date);
	}
}
