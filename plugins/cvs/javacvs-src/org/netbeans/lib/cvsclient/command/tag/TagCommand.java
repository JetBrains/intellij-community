/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Thomas Singer.
 * Portions created by Robert Greig are Copyright (C) 2001.
 * All Rights Reserved.
 *
 * Contributor(s): Thomas Singer.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command.tag;

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
 * The tag command adds or deleted a tag to the specified files/directories.
 *
 * @author  Thomas Singer
 */
public final class TagCommand extends AbstractCommand {

	// Constants ==============================================================

	@NonNls public static final String EXAM_DIR_TAG = "server: Tagging ";
	@NonNls public static final String EXAM_DIR_UNTAG = "server: Untagging ";

	// Fields =================================================================

	private String tag;
	private boolean checkThatUnmodified;
	private boolean deleteTag;
	private boolean allowMoveDeleteBranchTag;
	private boolean makeBranchTag;
	private boolean overrideExistingTag;

	// Setup ==================================================================

	public TagCommand() {
	}

	// Implemented ============================================================

	/**
	 * Execute the command.
	 *
	 * @param requestProcessor the client services object that provides any necessary
	 *               services to this command, including the ability to actually
	 *               process all the requests.
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

		final Requests requests = new Requests(CommandRequest.TAG, clientEnvironment);
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addArgumentRequest(isDeleteTag(), "-d");
		requests.addArgumentRequest(isMakeBranchTag(), "-b");
		requests.addArgumentRequest(isCheckThatUnmodified(), "-c");
		requests.addArgumentRequest(isOverrideExistingTag(), "-F");
		requests.addArgumentRequest(isAllowMoveDeleteBranchTag(), "-B");
		requests.addArgumentRequest(true, getTag());
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final IRequestsProgressHandler requestsProgressHandler = new FileStateRequestsProgressHandler(new RangeProgressViewer(progressViewer, 0.0, 0.5), cvsFiles);
		final ICvsListener responseProgressHandler = new FileInfoAndMessageResponseProgressHandler(new RangeProgressViewer(progressViewer, 0.5, 1.0), cvsFiles,
		                                                                                           isDeleteTag() ? EXAM_DIR_UNTAG : EXAM_DIR_TAG);
		final ICvsListener tagParser = new TagParser(eventManager, clientEnvironment.getCvsFileSystem());
		final ICvsListener listener = new DualListener(tagParser, responseProgressHandler);
		listener.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, requestsProgressHandler);
		}
		finally {
			listener.unregisterListeners(listenerRegistry);
		}
	}

	/**
	 * This method returns how the tag command would looklike when typed on the
	 * command line.
	 */
	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("tag ");
		cvsCommandLine.append(getCvsArguments());
		if (getTag() != null) {
			cvsCommandLine.append(getTag());
			cvsCommandLine.append(" ");
		}
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
		setCheckThatUnmodified(false);
		setDeleteTag(false);
		setAllowMoveDeleteBranchTag(false);
		setMakeBranchTag(false);
		setOverrideExistingTag(false);
	}

	// Accessing ==============================================================

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	private boolean isCheckThatUnmodified() {
		return checkThatUnmodified;
	}

	public void setCheckThatUnmodified(boolean checkThatUnmodified) {
		this.checkThatUnmodified = checkThatUnmodified;
	}

	private boolean isDeleteTag() {
		return deleteTag;
	}

	public void setDeleteTag(boolean deleteTag) {
		this.deleteTag = deleteTag;
	}

	public boolean isAllowMoveDeleteBranchTag() {
		return allowMoveDeleteBranchTag;
	}

	public void setAllowMoveDeleteBranchTag(boolean allowMoveDeleteBranchTag) {
		this.allowMoveDeleteBranchTag = allowMoveDeleteBranchTag;
	}

	private boolean isMakeBranchTag() {
		return makeBranchTag;
	}

	public void setMakeBranchTag(boolean makeBranchTag) {
		this.makeBranchTag = makeBranchTag;
	}

	private boolean isOverrideExistingTag() {
		return overrideExistingTag;
	}

	public void setOverrideExistingTag(boolean overrideExistingTag) {
		this.overrideExistingTag = overrideExistingTag;
	}

	// Utils ==================================================================

	private String getCvsArguments() {
		@NonNls final StringBuilder arguments = new StringBuilder();
		if (!isRecursive()) {
			arguments.append("-l ");
		}
		if (isDeleteTag()) {
			arguments.append("-d ");
		}
		if (isMakeBranchTag()) {
			arguments.append("-b ");
		}
		if (isCheckThatUnmodified()) {
			arguments.append("-c ");
		}
		if (isOverrideExistingTag()) {
			arguments.append("-F ");
		}
		if (isAllowMoveDeleteBranchTag()) {
			arguments.append("-B ");
		}
		return arguments.toString();
	}
}
