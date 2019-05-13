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
package org.netbeans.lib.cvsclient.command.admin;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public class AdminCommand extends AbstractCommand {

	// Fields =================================================================

	private boolean setLock;
	private boolean resetLock;

	// Setup ==================================================================

	public AdminCommand() {
	}

	// Implemented ============================================================

	@Override
        protected void resetCvsCommand() {
		super.resetCvsCommand();
		setSetLock(false);
		setResetLock(false);
	}

	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                  AuthenticationException {
		BugLog.getInstance().assertTrue(isSetLock() || isResetLock(), "Nothing specified");

		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(CommandRequest.ADMIN, clientEnvironment);
		requests.addArgumentRequest(isSetLock(), "-l");
		requests.addArgumentRequest(isResetLock(), "-u");
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final IRequestsProgressHandler requestsProgressHandler = FileStateRequestsProgressHandler.create(progressViewer, cvsFiles);
		return requestProcessor.processRequests(requests, requestsProgressHandler);
	}

	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("admin ");
		cvsCommandLine.append(getCvsArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	// Accessing ==============================================================

	public boolean isSetLock() {
		return setLock;
	}

	public void setSetLock(boolean setLock) {
		this.setLock = setLock;
	}

	public boolean isResetLock() {
		return resetLock;
	}

	public void setResetLock(boolean resetLock) {
		this.resetLock = resetLock;
	}

	// Utils ==================================================================

	private String getCvsArguments() {
		@NonNls final StringBuilder arguments = new StringBuilder();
		if (isSetLock()) {
			arguments.append("-l ");
		}
		if (isResetLock()) {
			arguments.append("-u ");
		}
		return arguments.toString();
	}
}
