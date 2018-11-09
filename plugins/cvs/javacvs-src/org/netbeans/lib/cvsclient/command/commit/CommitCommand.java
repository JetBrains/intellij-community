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
package org.netbeans.lib.cvsclient.command.commit;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.DualListener;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.progress.receiving.FileInfoAndMessageResponseProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;
import java.util.Date;

/**
 * The command to commit any changes that have been made.
 * @author  Robert Greig
 */
public final class CommitCommand extends AbstractCommand {

	// Constants ==============================================================

	@NonNls public static final String EXAM_DIR = "server: Examining ";

	// Fields =================================================================

	private String message;
	private boolean forceCommit;
	private boolean noModuleProgram;
	private String toRevisionOrBranch;

	// Setup ==================================================================

	public CommitCommand() {
	}

	// Implemented ============================================================

	/**
	 * Execute the command.
	 * @param requestProcessor the client services object that provides any necessary
	 *               services to this command, including the ability to actually
	 *               process all the requests
	 */
	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventSender, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                 AuthenticationException {
		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(CommandRequest.COMMIT, clientEnvironment);
		requests.addArgumentRequest(isForceCommit(), "-f");
		requests.addArgumentRequest(isRecursive(), "-R");
		requests.addArgumentRequest(isNoModuleProgram(), "-n");
		requests.addArgumentRequest(getToRevisionOrBranch(), "-r");
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addMessageRequests(CommandUtils.getMessageNotNull(getMessage()));
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final IRequestsProgressHandler requestsProgressHandler = new FileStateRequestsProgressHandler(new RangeProgressViewer(progressViewer, 0.0, 0.5), cvsFiles);
		final ICvsListener responseProgressHandler = new FileInfoAndMessageResponseProgressHandler(new RangeProgressViewer(progressViewer, 0.5, 1.0), cvsFiles, EXAM_DIR);

		final ICvsListener commitParser = new CommitParser(eventSender, clientEnvironment.getCvsFileSystem());
		final ICvsListener parser = new DualListener(commitParser, responseProgressHandler);
		parser.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, requestsProgressHandler);
		}
		finally {
			parser.unregisterListeners(listenerRegistry);
		}
	}

	@Override
        protected boolean isModified(FileObject fileObject, Date entryLastModified, IClientEnvironment clientEnvironment) {
		if (isForceCommit()) {
			return true;
		}
		return super.isModified(fileObject, entryLastModified, clientEnvironment);
	}

	/**
	 * This method returns how the command would looklike when typed on the command line.
	 * Example: checkout -p CvsCommand.java
	 * @return <command's name> [<parameters>] files/dirs
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("commit ");
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
		setMessage(null);
		setRecursive(true);
		setForceCommit(false);
		setNoModuleProgram(false);
		setToRevisionOrBranch(null);
	}

	// Accessing ==============================================================

	private String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	private boolean isForceCommit() {
		return forceCommit;
	}

	public void setForceCommit(boolean forceCommit) {
		this.forceCommit = forceCommit;
	}

	private boolean isNoModuleProgram() {
		return noModuleProgram;
	}

	public void setNoModuleProgram(boolean noModuleProgram) {
		this.noModuleProgram = noModuleProgram;
	}

	private String getToRevisionOrBranch() {
		return toRevisionOrBranch;
	}

	public void setToRevisionOrBranch(String toRevisionOrBranch) {
		this.toRevisionOrBranch = toRevisionOrBranch;
	}

	// Utils ==================================================================

	private String getCvsArguments() {
		@NonNls final StringBuilder arguments = new StringBuilder();
		if (!isRecursive()) {
			arguments.append("-l ");
		}
		if (isForceCommit()) {
			arguments.append("-f ");
			if (isRecursive()) {
				arguments.append("-R ");
			}
		}
		if (isNoModuleProgram()) {
			arguments.append("-n ");
		}
		if (getToRevisionOrBranch() != null) {
			arguments.append("-r ");
			arguments.append(getToRevisionOrBranch());
			arguments.append(" ");
		}
		if (getMessage() != null) {
			arguments.append("-m \"");
			arguments.append(CommandUtils.getMessageNotNull(getMessage()));
			arguments.append("\" ");
		}
		return arguments.toString();
	}
}
