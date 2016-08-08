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
package org.netbeans.lib.cvsclient.command;

import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author  Thomas Singer
 */
public abstract class AbstractCommand extends Command {

	// Fields =================================================================

	private final List<AbstractFileObject> fileObjects = new ArrayList<>();
	private boolean recursive = true;

	// Setup ==================================================================

	protected AbstractCommand() {
	}

	// Accessing ==============================================================

	protected final boolean isRecursive() {
		return recursive;
	}

	public final void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}

	public void addFileObject(@NotNull AbstractFileObject file) {
		fileObjects.add(file);
	}

	public final List<AbstractFileObject> getFileObjects() {
		return Collections.unmodifiableList(fileObjects);
	}

	// Actions ================================================================

	protected final void addArgumentRequests(Requests requests) {
		for (final AbstractFileObject fileObject : fileObjects) {
			requests.addFileArgumentRequest(fileObject);
		}
	}

	protected final ICvsFiles scanFileSystem(IClientEnvironment clientEnvironment) throws IOException {
		final CvsFiles cvsFiles = new CvsFiles();
		new FileSystemScanner(clientEnvironment, isRecursive()).scan(getFileObjects(), cvsFiles);
		return cvsFiles;
	}

	protected final void addFileRequests(ICvsFiles cvsFiles, final Requests requests, final IClientEnvironment clientEnvironment) {
		cvsFiles.visit(new ICvsFilesVisitor() {
			public void handleFile(FileObject fileObject, Entry entry, boolean exists) {
				addRequestForFile(fileObject, entry, exists, requests, clientEnvironment);
			}

			public void handleDirectory(DirectoryObject directoryObject) {
				requests.addDirectoryStickyRequests(directoryObject, clientEnvironment.getAdminReader(), clientEnvironment.getCvsFileSystem());
			}
		});
	}

	protected void addRequestForFile(FileObject fileObject, Entry entry, boolean fileExists, Requests requests, IClientEnvironment clientEnvironment) {
		BugLog.getInstance().assertNotNull(fileObject);
		BugLog.getInstance().assertNotNull(requests);
		BugLog.getInstance().assertNotNull(clientEnvironment);

		if (entry == null) {
			if (!clientEnvironment.getIgnoreFileFilter().shouldBeIgnored(fileObject, clientEnvironment.getCvsFileSystem())) {
				requests.addQuestionableRequest(fileObject);
			}
			return;
		}

		// for deleted added files, don't send anything..
		if (entry.isAddedFile() && !fileExists) {
			return;
		}

		final Date entryLastModified = entry.getLastModified();
		final boolean hadConflicts = entry.isConflict();
		if (!hadConflicts) {
			// we null out the conflict field if there is no conflict
			// because we use that field to store the timestamp of the
			// file (just like command-line CVS). There is no point
			// in sending this information to the CVS server, even
			// though it should be ignored by the server.
			entry.parseConflictString(null);
		}
		requests.addEntryRequest(entry);

		if (!fileExists || entry.isRemoved()) {
			return;
		}

		if (!hadConflicts && entryLastModified != null) {
			if (!isModified(fileObject, entryLastModified, clientEnvironment)) {
				requests.addUnchangedRequest(fileObject);
				return;
			}
		}

		addModifiedRequest(fileObject, entry, requests, clientEnvironment);
	}

	protected boolean isModified(FileObject fileObject, Date entryLastModified, IClientEnvironment clientEnvironment) {
		return clientEnvironment.getAdminReader().isModified(fileObject, entryLastModified, clientEnvironment.getCvsFileSystem());
	}

	protected void addModifiedRequest(FileObject fileObject, Entry entry, Requests requests, IClientEnvironment clientEnvironment) {
		final boolean writable = clientEnvironment.getLocalFileReader().isWritable(fileObject, clientEnvironment.getCvsFileSystem());
		requests.addModifiedRequest(fileObject, entry.isBinary(), writable);
	}

	protected final void appendFileArguments(StringBuffer buffer) {
		boolean appendSpace = false;
		for (AbstractFileObject fileObject : fileObjects) {
			if (appendSpace) {
				buffer.append(' ');
			}
			buffer.append(fileObject.toUnixPath());
			appendSpace = true;
		}
	}
}
