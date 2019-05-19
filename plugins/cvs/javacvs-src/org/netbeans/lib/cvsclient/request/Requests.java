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
package org.netbeans.lib.cvsclient.request;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author  Thomas Singer
 */
public final class Requests {

	// Fields =================================================================

	private final List<IRequest> requestList = new ArrayList<>();
	private final ICvsFileSystem cvsFileSystem;
	private final IAdminReader adminReader;
	private final ResponseExpectingRequest responseExpectingRequest;

	// Setup ==================================================================

	public Requests(ResponseExpectingRequest responseExpectingRequest, IClientEnvironment clientEnvironment) {
		BugLog.getInstance().assertNotNull(responseExpectingRequest);
		BugLog.getInstance().assertNotNull(clientEnvironment);

		this.responseExpectingRequest = responseExpectingRequest;
		this.cvsFileSystem = clientEnvironment.getCvsFileSystem();
		this.adminReader = clientEnvironment.getAdminReader();
	}

	// Accessing ==============================================================

	public void addRequest(IRequest request) {
		BugLog.getInstance().assertNotNull(request);

		requestList.add(request);
	}

	public void addArgumentRequest(@NonNls String argument) {
		addRequest(new ArgumentRequest(argument));
	}

	public void addArgumentRequest(Object obj, @NonNls String argument) {
		if (obj == null) {
			return;
		}

		final String objString = obj.toString().trim();
		if (objString.length() == 0) {
			return;
		}

		addArgumentRequest(argument);
		addArgumentRequest(objString);
	}

	public void addArgumentRequests(Object obj, @NonNls String argument) {
		if (obj == null) {
			return;
		}

		final String objString = obj.toString().trim();
		if (objString.length() == 0) {
			return;
		}

		addArgumentRequest(argument);
		addArgumentRequest(objString);
	}

	public void addArgumentRequest(boolean value, @NonNls String argument) {
		if (value) {
			addArgumentRequest(argument);
		}
	}

	public void addLocalPathDirectoryRequest() {
		//final String repositoryPath = cvsFileSystem.getRepositoryForDirectory(DirectoryObject.getRoot(), adminReader);
		//addRequest(new LocalDirectoryRequest(repositoryPath));
          addDirectoryRequest(DirectoryObject.getRoot());
	}

	@NonNls public String addDirectoryRequest(DirectoryObject directoryObject) {
		final String relativeDirPath = directoryObject.toUnixPath();
		final String repositoryPath = cvsFileSystem.getRepositoryForDirectory(directoryObject, adminReader);
		addRequest(new DirectoryRequest(relativeDirPath, repositoryPath));
        if (adminReader.isStatic(directoryObject, cvsFileSystem)){
            addRequest(new AbstractRequest() {
                @Override
                public String getRequestString() {
                    return "Static-directory \n";
                }
            });
        }
		return repositoryPath;
	}

	public void addFileArgumentRequest(AbstractFileObject fileObject) {
		addArgumentRequest(fileObject.toUnixPath());
	}

	public void addMessageRequests(@NonNls String message) {
		addArgumentRequest("-m");
		boolean first = true;
		final StringTokenizer token = new StringTokenizer(message, "\n", false);
		while (token.hasMoreTokens()) {
			if (first) {
				addArgumentRequest(token.nextToken());
				first = false;
			}
			else {
				addRequest(new ArgumentxRequest(token.nextToken()));
			}
		}
	}

	public List<IRequest> getRequests() {
		return Collections.unmodifiableList(requestList);
	}

	public ResponseExpectingRequest getResponseExpectingRequest() {
		return responseExpectingRequest;
	}

	public void addDirectoryStickyRequests(DirectoryObject directoryObject, IAdminReader adminReader, ICvsFileSystem cvsFileSystem) {
		addDirectoryRequest(directoryObject);
		addStickyRequest(adminReader.getStickyTagForDirectory(directoryObject, cvsFileSystem));
	}

	public void addStickyRequest(String tag) {
		if (tag != null) {
			addRequest(new StickyRequest(tag));
		}
	}

	public void addModifiedRequest(FileObject fileObject, boolean binary, boolean writable) {
		addRequest(new ModifiedRequest(fileObject, binary, writable));
	}

	public void addIsModifiedRequest(FileObject fileObject) {
		addRequest(new IsModifiedRequest(fileObject));
	}

	public void addUnchangedRequest(FileObject fileObject) {
		addRequest(new UnchangedRequest(fileObject));
	}

	public void addEntryRequest(Entry entry) {
		addRequest(new EntryRequest(entry));
	}

	public void addQuestionableRequest(FileObject fileObject) {
		addRequest(new QuestionableRequest(fileObject));
	}

	public void addNotifyRequest(FileObject fileObject, @NonNls String command, String temporaryWatch) {
		final String path = cvsFileSystem.getLocalFileSystem().getFile(fileObject.getParent()).getAbsolutePath();
		addRequest(new NotifyRequest(fileObject, path, command, temporaryWatch));
	}

	public void addKoptRequest(KeywordSubstitution keywordSubstMode) {
		addRequest(new KoptRequest(keywordSubstMode));
	}
}
