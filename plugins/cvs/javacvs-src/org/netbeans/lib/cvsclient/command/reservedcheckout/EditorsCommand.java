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
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class EditorsCommand extends AbstractCommand {

	// Setup ==================================================================

	public EditorsCommand() {
	}

	// Implemented ============================================================

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

		final Requests requests = new Requests(CommandRequest.EDITORS, clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final ICvsListener builder = new EditorsMessageParser(eventSender, clientEnvironment.getCvsFileSystem(), cvsFiles);
		builder.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
		}
		finally {
			builder.unregisterListeners(listenerRegistry);
		}
	}

	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("editors ");
		cvsCommandLine.append(getCVSArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
	}

	// Utils ==================================================================

	/**
	 * Returns the arguments of the command in the command-line style.
	 * Similar to getCVSCommand() however without the files and command's name
	 */
	private String getCVSArguments() {
		@NonNls final StringBuilder toReturn = new StringBuilder();
		if (!isRecursive()) {
			toReturn.append("-l ");
		}
		return toReturn.toString();
	}
}
