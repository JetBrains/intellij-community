/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2001 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command.watchers;

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
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * The watchers command looks up who is watching this file,
 * who is interested in it.
 *
 * @author Thomas Singer
 */
public final class WatchersCommand extends AbstractCommand {

	// Setup ==================================================================

	public WatchersCommand() {
	}

	// Implemented ============================================================

	/**
	 * Executes this command.
	 *
	 * @param requestProcessor the client services object that provides any necessary
	 *               services to this command, including the ability to actually
	 *               process all the requests
	 */
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

		final Requests requests = new Requests(CommandRequest.WATCHERS, clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
	}

	/**
	 * This method returns how the command would looklike when typed on the command line.
	 * Each command is responsible for constructing this information.
	 * @return <command's name> [<parameters>] files/dirs. Example: checkout -p CvsCommand.java
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("watchers ");
		cvsCommandLine.append(getCvsArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	/**
	 * resets all switches in the command. After calling this method,
	 * the command should have no switches defined and should behave defaultly.
	 */
	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
	}

	// Utils ==================================================================

	private String getCvsArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}
		return cvsArguments.toString();
	}
}
