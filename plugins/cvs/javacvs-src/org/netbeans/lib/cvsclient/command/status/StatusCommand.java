/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/

 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.

 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command.status;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.DualListener;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.progress.receiving.FileInfoAndMessageResponseProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * The status command looks up the status of files in the repository
 * @author  Robert Greig
 */
public final class StatusCommand extends AbstractCommand {

	// Constants ==============================================================

	@NonNls static final String EXAM_DIR = "server: Examining ";

	// Fields =================================================================

	private boolean includeTags;

	// Setup ==================================================================

	public StatusCommand() {
	}

	/**
	 * Execute a command
	 * @param requestProcessor the client services object that provides any necessary
	 * services to this command, including the ability to actually process
	 * all the requests.
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

		final Requests requests = new Requests(CommandRequest.STATUS, clientEnvironment);
		requests.addArgumentRequest(includeTags, "-v");
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final IRequestsProgressHandler requestsProgressHandler = new FileStateRequestsProgressHandler(new RangeProgressViewer(progressViewer, 0.0, 0.5), cvsFiles);
		final ICvsListener responseProgressHandler = new FileInfoAndMessageResponseProgressHandler(new RangeProgressViewer(progressViewer, 0.5, 1.0), cvsFiles, EXAM_DIR);

		final ICvsListener statusMessageParser = new StatusMessageParser(eventSender, getFileObjects(), clientEnvironment.getCvsFileSystem());
		final ICvsListener listener = new DualListener(statusMessageParser, responseProgressHandler);
		listener.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, requestsProgressHandler);
		}
		finally {
			listener.unregisterListeners(listenerRegistry);
		}
	}

	/**
	 * resets all switches in the command. After calling this method,
	 * the command should have no switches defined and should behave defaultly.
	 */
	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
		setIncludeTags(false);
	}

	/**
	 * This method returns how the command would looklike when typed on the command line.
	 * Each command is responsible for constructing this information.
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommand = new StringBuffer("status ");
		cvsCommand.append(getCVSArguments());
		appendFileArguments(cvsCommand);
		return cvsCommand.toString();
	}

	// Accessing ==============================================================

	private boolean isIncludeTags() {
		return includeTags;
	}

	public void setIncludeTags(boolean includeTags) {
		this.includeTags = includeTags;
	}

	// Utils ==================================================================

	private String getCVSArguments() {
		@NonNls final StringBuilder toReturn = new StringBuilder();
		if (isIncludeTags()) {
			toReturn.append("-v ");
		}
		if (!isRecursive()) {
			toReturn.append("-l ");
		}
		return toReturn.toString();
	}

}
