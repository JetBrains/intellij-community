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
package org.netbeans.lib.cvsclient.command.log;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.DualListener;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.progress.receiving.FileInfoAndMessageResponseProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.FileStateRequestsProgressHandler;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public class LogCommand extends AbstractCommand {

	// Constants ==============================================================

	@NonNls private static final String EXAM_DIR = " server: Logging ";

	// Fields =================================================================

	private boolean defaultBranch;
	private String dateFilter;
	private boolean headerOnly;
	private boolean noTags;
	private String revisionFilter;
	private String stateFilter;
	private String userFilter;
	private boolean headerAndDescOnly;

	// Setup ==================================================================

	public LogCommand() {
	}

	// Implemented ============================================================

	@Override
        public final boolean execute(IRequestProcessor requestProcessor, IEventSender eventSender, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                       AuthenticationException {
		final ICvsFiles cvsFiles;
		try {
			cvsFiles = scanFileSystem(clientEnvironment);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final Requests requests = new Requests(CommandRequest.LOG, clientEnvironment);
		requests.addArgumentRequest(isDefaultBranch(), "-b");
		requests.addArgumentRequest(isHeaderAndDescOnly(), "-t");
		requests.addArgumentRequest(isHeaderOnly(), "-h");
		requests.addArgumentRequest(isNoTags(), "-N");
		requests.addArgumentRequest(getUserFilter(), "-w");
		requests.addArgumentRequest(getRevisionFilter(), "-r");
		requests.addArgumentRequest(getStateFilter(), "-s");
		requests.addArgumentRequest(getDateFilter(), "-d");
		addFileRequests(cvsFiles, requests, clientEnvironment);
		requests.addLocalPathDirectoryRequest();
		addArgumentRequests(requests);

		final IRequestsProgressHandler requestsProgressHandler = new FileStateRequestsProgressHandler(new RangeProgressViewer(progressViewer, 0.0, 0.5), cvsFiles);
		final ICvsListener responseProgressHandler = new FileInfoAndMessageResponseProgressHandler(new RangeProgressViewer(progressViewer, 0.5, 1.0), cvsFiles, EXAM_DIR);

		final ICvsListener parser = createParser(eventSender, clientEnvironment.getCvsFileSystem());
		final ICvsListener listener = new DualListener(parser, responseProgressHandler);
		listener.registerListeners(listenerRegistry);
		try {
			return requestProcessor.processRequests(requests, requestsProgressHandler);
		}
		finally {
			listener.unregisterListeners(listenerRegistry);
		}
	}

	protected ICvsListener createParser(IEventSender eventSender, ICvsFileSystem cvsFileSystem) {
		return new LogMessageParser(eventSender, cvsFileSystem);
	}

	@Override
        public final String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("log ");
		cvsCommandLine.append(getCVSArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	@Override
        public final void resetCvsCommand() {
		super.resetCvsCommand();
		setRecursive(true);
		setDefaultBranch(false);
		setHeaderOnly(false);
		setHeaderAndDescOnly(false);
		setNoTags(false);
		setDateFilter(null);
		setRevisionFilter(null);
		setStateFilter(null);
		setUserFilter(null);
	}

	/**
	 * Getter for property defaultBranch, equals the command-line CVS switch
	 * "-b".
	 * @return Value of property defaultBranch.
	 */
	private boolean isDefaultBranch() {
		return defaultBranch;
	}

	/**
	 * Setter for property defaultBranch, equals the command-line CVS switch
	 * "-b".
	 * @param defaultBranch New value of property defaultBranch.
	 */
	public final void setDefaultBranch(boolean defaultBranch) {
		this.defaultBranch = defaultBranch;
	}

	/**
	 * Getter for property dateFilter, equals the command-line CVS switch "-d".
	 * @return Value of property dateFilter.
	 */
	private String getDateFilter() {
		return dateFilter;
	}

	/** Setter for property dateFilter, equals the command-line CVS switch "-d".
	 * @param dateFilter New value of property dateFilter.
	 */
	public final void setDateFilter(String dateFilter) {
		this.dateFilter = dateFilter;
	}

	/** Getter for property headerOnly, equals the command-line CVS switch "-h".
	 * @return Value of property headerOnly.
	 */
	private boolean isHeaderOnly() {
		return headerOnly;
	}

	/** Setter for property headerOnly, equals the command-line CVS switch "-h".
	 * @param headerOnly New value of property headerOnly.
	 */
	public final void setHeaderOnly(boolean headerOnly) {
		this.headerOnly = headerOnly;
	}

	/** Getter for property noTags, equals the command-line CVS switch "-N".
	 * @return Value of property noTags.
	 */
	private boolean isNoTags() {
		return noTags;
	}

	/** Setter for property noTags, equals the command-line CVS switch "-N".
	 * @param noTags New value of property noTags.
	 */
	public final void setNoTags(boolean noTags) {
		this.noTags = noTags;
	}

	/** Getter for property revisionFilter, equals the command-line CVS switch "-r".
	 * @return Value of property revisionFilter.
	 */
	private String getRevisionFilter() {
		return revisionFilter;
	}

	/** Setter for property revisionFilter, equals the command-line CVS switch "-r".
	 * @param revisionFilter New value of property revisionFilter.
	 empty string means latest revision of default branch.
	 */
	public final void setRevisionFilter(String revisionFilter) {
		this.revisionFilter = revisionFilter;
	}

	/** Getter for property stateFilter, equals the command-line CVS switch "-s".
	 * @return Value of property stateFilter.
	 */
	private String getStateFilter() {
		return stateFilter;
	}

	/** Setter for property stateFilter, equals the command-line CVS switch "-s".
	 * @param stateFilter New value of property stateFilter.
	 */
	public final void setStateFilter(String stateFilter) {
		this.stateFilter = stateFilter;
	}

	/** Getter for property userFilter, equals the command-line CVS switch "-w".
	 * @return Value of property userFilter,  empty string means the current user.
	 */
	private String getUserFilter() {
		return userFilter;
	}

	/** Setter for property userFilter, equals the command-line CVS switch "-w".
	 * @param userFilter New value of property userFilter.
	 */
	public final void setUserFilter(String userFilter) {
		this.userFilter = userFilter;
	}

	/** Getter for property headerAndDescOnly, equals the command-line CVS switch "-t".
	 * @return Value of property headerAndDescOnly.
	 */
	private boolean isHeaderAndDescOnly() {
		return headerAndDescOnly;
	}

	/** Setter for property headerAndDescOnly, equals the command-line CVS switch "-t".
	 * @param headerAndDescOnly New value of property headerAndDescOnly.
	 */
	public final void setHeaderAndDescOnly(boolean headerAndDescOnly) {
		this.headerAndDescOnly = headerAndDescOnly;
	}

	/**
	 * Returns the arguments of the command in the command-line style.
	 * Similar to getCVSCommand() however without the files and command's name
	 */
	private String getCVSArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		if (isDefaultBranch()) {
			cvsArguments.append("-b ");
		}
		if (isHeaderAndDescOnly()) {
			cvsArguments.append("-t ");
		}
		if (isHeaderOnly()) {
			cvsArguments.append("-h ");
		}
		if (isNoTags()) {
			cvsArguments.append("-N ");
		}
		if (!isRecursive()) {
			cvsArguments.append("-l ");
		}
		if (userFilter != null) {
			cvsArguments.append("-w");
			cvsArguments.append(userFilter);
			cvsArguments.append(' ');
		}
		if (revisionFilter != null) {
			cvsArguments.append("-r");
			cvsArguments.append(revisionFilter);
			cvsArguments.append(' ');
		}
		if (stateFilter != null) {
			cvsArguments.append("-s");
			cvsArguments.append(stateFilter);
			cvsArguments.append(' ');
		}
		if (dateFilter != null) {
			cvsArguments.append("-d");
			cvsArguments.append(dateFilter);
			cvsArguments.append(' ');
		}
		return cvsArguments.toString();
	}

	// Utils ==================================================================

	@Override
        protected final void addModifiedRequest(FileObject fileObject, Entry entry, Requests requests, IClientEnvironment clientEnvironment) {
		requests.addIsModifiedRequest(fileObject);
	}
}
