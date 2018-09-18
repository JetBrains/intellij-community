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
package org.netbeans.lib.cvsclient.command.reservedcheckout;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class UneditCommand extends AbstractCommand {

	// Fields =================================================================

	private Watch temporaryWatch;

	// Setup ==================================================================

	public UneditCommand() {
	}

	// Implemented ============================================================

	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                  AuthenticationException {
		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(CommandRequest.NOOP, clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();

		return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
	}

	@Override
        protected void addRequestForFile(FileObject fileObject, Entry entry, boolean fileExists, Requests requests, IClientEnvironment clientEnvironment) {
		if (!fileExists || entry == null) {
			return;
		}

		requests.addNotifyRequest(fileObject, "U", Watch.getWatchString(getTemporaryWatch()));

		try {
			clientEnvironment.getAdminWriter().uneditFile(fileObject, clientEnvironment.getCvsFileSystem(), clientEnvironment.getFileReadOnlyHandler());
		}
		catch (IOException ex) {
			// ignore
		}
	}

	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("unedit ");
		cvsCommandLine.append(getCVSArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
	}

	// Accessing ==============================================================

	private Watch getTemporaryWatch() {
		return temporaryWatch;
	}

	public void setTemporaryWatch(Watch temporaryWatch) {
		this.temporaryWatch = temporaryWatch;
	}

	// Utils ==================================================================

	private String getCVSArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}
		return cvsArguments.toString();
	}
}
