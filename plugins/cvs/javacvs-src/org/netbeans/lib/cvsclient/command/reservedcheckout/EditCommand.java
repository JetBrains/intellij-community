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
import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.*;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author  Thomas Singer
 */
public final class EditCommand extends AbstractCommand {

	// Fields =================================================================

	private boolean checkThatUnedited;
	private boolean forceEvenIfEdited;
	private Watch temporaryWatch;
	private boolean editors;

	// Setup ==================================================================

	public EditCommand() {
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

		if (isCheckThatUnedited()) {
			editors = true;

			final Requests requests = new Requests(CommandRequest.EDITORS, clientEnvironment);
			addFileRequests(cvsFiles, requests, clientEnvironment);
			requests.addLocalPathDirectoryRequest();
			addArgumentRequests(requests);

			final EditEditorsMessageParser parser = new EditEditorsMessageParser(clientEnvironment.getCvsRoot().getUser());
			parser.registerListeners(listenerRegistry);
			try {
				final RangeProgressViewer editorsProgressViewer = new RangeProgressViewer(progressViewer, 0.0, 0.5);
				requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(editorsProgressViewer, cvsFiles));
			}
			finally {
				parser.unregisterListeners(listenerRegistry);
			}

			if (parser.isFilesEdited()) {
                          final String message = JavaCvsSrcBundle.message("cannot.edit.files.they.are.edited.error.message");
                          eventSender.notifyMessageListeners(message.getBytes(StandardCharsets.UTF_8), true, false);
				return false;
			}

			progressViewer = new RangeProgressViewer(progressViewer, 0.5, 1.0);
		}

		editors = false;

		final Requests requests = new Requests(CommandRequest.NOOP, clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addArgumentRequest(isCheckThatUnedited(), "-c");
		requests.addArgumentRequest(isForceEvenIfEdited(), "-f");
		requests.addLocalPathDirectoryRequest();

		return requestProcessor.processRequests(requests, FileStateRequestsProgressHandler.create(progressViewer, cvsFiles));
	}

	@Override
        protected void addRequestForFile(FileObject fileObject, Entry entry, boolean fileExists, Requests requests, IClientEnvironment clientEnvironment) {
		if (editors) {
			super.addRequestForFile(fileObject, entry, fileExists, requests, clientEnvironment);
			return;
		}

		if (!fileExists || entry == null) {
			return;
		}

		requests.addNotifyRequest(fileObject, "E", Watch.getWatchString(getTemporaryWatch()));

		try {
			clientEnvironment.getAdminWriter().editFile(fileObject, entry, clientEnvironment.getCvsFileSystem(), clientEnvironment.getFileReadOnlyHandler());
		}
		catch (IOException ex) {
			// ignore
		}
	}

	/**
	 * This method returns how the tag command would looklike when typed on the command line.
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("edit ");
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
		setCheckThatUnedited(false);
		setForceEvenIfEdited(true);
		setTemporaryWatch(null);
	}

	// Accessing ==============================================================

	/**
	 * Returns the arguments of the command in the command-line style.
	 * Similar to getCVSCommand() however without the files and command's name
	 */
	private String getCvsArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}
		return cvsArguments.toString();
	}

	/**
	 * Returns whether to check for unedited files.
	 */
	private boolean isCheckThatUnedited() {
		return checkThatUnedited;
	}

	/**
	 * Sets whether to check for unedited files.
	 * This is cvs' -c option.
	 */
	public final void setCheckThatUnedited(boolean checkThatUnedited) {
		this.checkThatUnedited = checkThatUnedited;
	}

	/**
	 * Returns whether the edit is forces even if the files are edited.
	 */
	private boolean isForceEvenIfEdited() {
		return forceEvenIfEdited;
	}

	/**
	 * Sets whether the edit is forces even if the files are edited.
	 * This is cvs' -f option.
	 */
	public final void setForceEvenIfEdited(boolean forceEvenIfEdited) {
		this.forceEvenIfEdited = forceEvenIfEdited;
	}

	/**
	 * Returns the temporary watch.
	 */
	private Watch getTemporaryWatch() {
		return temporaryWatch;
	}

	/**
	 * Sets the temporary watch.
	 * This is cvs' -a option.
	 */
	public final void setTemporaryWatch(Watch temporaryWatch) {
		this.temporaryWatch = temporaryWatch;
	}
}
