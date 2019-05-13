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
package org.netbeans.lib.cvsclient.command.watch;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * @author Thomas Singer
 */
public final class WatchCommand extends AbstractCommand {

	// Fields =================================================================

	private WatchMode watchMode;
	private Watch watch;

	// Setup ==================================================================

	public WatchCommand() {
	}

	// Implemented ============================================================

	/**
	 * Executes this command.
	 *
	 * @param requestProcessor the client services object that provides any necessary
	 *               services to this command, including the ability to actually
	 *               process all the requests
	 * @param eventManager the EventManager used for sending events
	 *
	 * @throws IllegalStateException if the commands options aren't set correctly
	 * @throws CommandException if some other thing gone wrong
	 */
	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                  AuthenticationException {
		checkState();

		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(getWatchMode().getCommand(), clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		if (getWatchMode().isWatchOptionAllowed()) {
			final String[] arguments = getWatchNotNull().getArguments();
			for (String argument : arguments) {
				requests.addArgumentRequest("-a");
				requests.addArgumentRequest(argument);
			}
		}
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
	}

	/**
	 * Resets all switches in this command.
	 * After calling this method, the command behaves like newly created.
	 */
	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
		setWatch(null);
	}

	/**
	 * Returns how this command would look like when typed on the command line.
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommand = new StringBuffer("watch ");
		cvsCommand.append(getCVSArguments());
		appendFileArguments(cvsCommand);
		return cvsCommand.toString();
	}

	// Accessing ==============================================================

	private WatchMode getWatchMode() {
		return watchMode;
	}

	public void setWatchMode(WatchMode watchMode) {
		this.watchMode = watchMode;
	}

	public Watch getWatch() {
		return watch;
	}

	/**
	 * Sets the watch.
	 * If the WatchMode ADD or REMOVE is used, null is the same as Watch.ALL.
	 * If the WatchMode ON or OFF is used, this option isn't used at all.
	 */
	public void setWatch(Watch watch) {
		this.watch = watch;
	}


	// Utils ==================================================================

	private Watch getWatchNotNull() {
		if (watch == null) {
			return Watch.ALL;
		}
		return watch;
	}

	/**
	 * Returns the arguments of the command in the command-line style.
	 * Similar to getCVSCommand() however without the files and command's name
	 */
	private String getCVSArguments() {
		checkState();

		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		cvsArguments.append(getWatchMode().toString());
		cvsArguments.append(' ');

		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}

		if (getWatchMode().isWatchOptionAllowed()) {
			cvsArguments.append("-a ");
			cvsArguments.append(getWatchNotNull().toString());
		}
		return cvsArguments.toString();
	}

	private void checkState() {
		if (getWatchMode() == null) {
			throw new IllegalStateException("Watch mode expected!");
		}
	}
}
