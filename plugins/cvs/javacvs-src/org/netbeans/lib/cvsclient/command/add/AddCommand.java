/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command.add;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds a file or directory.
 *
 * When adding two directories, the following requests must be sent:
 *   Directory test3
 *   O:\CVS/compile/test3
 *   .............................
 *   Directory test2
 *   O:\CVS/compile/test2
 *   -----------------------------
 *   Directory .
 *   O:\CVS/compile
 *   -----------------------------
 *   Argument test3
 *   .............................
 *   Argument test2
 *   -----------------------------
 *   add
 *
 * When adding two files, the following requests must be sent:
 *   Directory test2
 *   O:\CVS/compile/test2
 *   ...............................
 *   Modified test2file.txt
 *   u=rw,g=r,o=r
 *   (Sending file: D:\test2\test2\test2file.txt)
 *   ---------------------------------------
 *   Directory test3
 *   O:\CVS/compile/test3
 *   .................................
 *   Modified test2file.txt
 *   u=rw,g=r,o=r
 *   (Sending file: D:\test2\test3\test2file.txt)
 *   -----------------------------------------
 *   Directory .
 *   O:\CVS/compile
 *   -----------------------------------------
 *   Argument test2/test2file.txt
 *   .....................................
 *   Argument test3/test2file.txt
 *   -------------------------------------
 *   add
 *
 * @author  Robert Greig
 */
public final class AddCommand extends AbstractCommand {

	// Fields =================================================================

	private final Map<String, DirectoryObject> repositoryPathToDirectoryObject = new HashMap<>();

	private KeywordSubstitution keywordSubst;

	// Setup ==================================================================

	public AddCommand() {
	}

	// Implemented ============================================================

	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer)
          throws CommandException, AuthenticationException {
		BugLog.getInstance().assertTrue(getFileObjects().size() > 0, "No file specified.");

		repositoryPathToDirectoryObject.clear();

		final Requests requests;
		try {
			requests = new Requests(CommandRequest.ADD, clientEnvironment);

			requests.addArgumentRequest(getKeywordSubst(), "-k");

			for (AbstractFileObject abstractFileObject : getFileObjects()) {
				addRequests(abstractFileObject, requests, requestProcessor, clientEnvironment);
			}

			requests.addLocalPathDirectoryRequest();
			addArgumentRequests(requests);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		final ICvsListener parser = new AddParser(eventManager, clientEnvironment.getCvsFileSystem());
		parser.registerListeners(listenerRegistry);
		try {
			final boolean result = requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());

			createCvsDirectories(clientEnvironment);

			return result;
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}
		finally {
			repositoryPathToDirectoryObject.clear();
			parser.unregisterListeners(listenerRegistry);
		}
	}

	@Override
        public String getCvsCommandLine() {
		@NonNls final StringBuffer cvsCommandLine = new StringBuffer("add ");
		cvsCommandLine.append(getCvsArguments());
		appendFileArguments(cvsCommandLine);
		return cvsCommandLine.toString();
	}

	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setKeywordSubst(null);
	}

	// Accessing ==============================================================

	private KeywordSubstitution getKeywordSubst() {
		return keywordSubst;
	}

	public void setKeywordSubst(KeywordSubstitution keywordSubst) {
		this.keywordSubst = keywordSubst;
	}

	// Utils ==================================================================

	private void createCvsDirectories(IClientEnvironment clientEnvironment) throws IOException {
		for (AbstractFileObject abstractFileObject : getFileObjects()) {
			if (!abstractFileObject.isDirectory()) {
				continue;
			}

			clientEnvironment.getAdminWriter().directoryAdded((DirectoryObject)abstractFileObject, clientEnvironment.getCvsFileSystem());
		}
	}

	private String getCvsArguments() {
		@NonNls final StringBuilder toReturn = new StringBuilder();
		if (getKeywordSubst() != null) {
			toReturn.append("-k");
			toReturn.append(getKeywordSubst().toString());
			toReturn.append(" ");
		}
		return toReturn.toString();
	}

	private void addRequests(AbstractFileObject abstractFileObject, Requests requests, IRequestProcessor requestProcessor, IClientEnvironment clientEnvironment) throws IOException {
		if (abstractFileObject.isDirectory()) {
			addRequestsForDirectory((DirectoryObject)abstractFileObject, requests, requestProcessor, clientEnvironment);
		}
		else {
			addRequestsForFile((FileObject)abstractFileObject, requests, clientEnvironment);
		}
	}

	private void addRequestsForDirectory(DirectoryObject directoryObject, Requests requests, IRequestProcessor requestProcessor, IClientEnvironment clientEnvironment) {
		final DirectoryObject parentDirectoryUnderCvsControl = addDirectoryRequestsUpToLocalDirectory(directoryObject, requests, requestProcessor, clientEnvironment);
		if (parentDirectoryUnderCvsControl == null) {
			return;
		}

		final String tag = clientEnvironment.getAdminReader().getStickyTagForDirectory(parentDirectoryUnderCvsControl, clientEnvironment.getCvsFileSystem());
		requests.addStickyRequest(tag);
	}

	private DirectoryObject addDirectoryRequestsUpToLocalDirectory(DirectoryObject directoryObject, Requests requests, IRequestProcessor requestProcessor, IClientEnvironment clientEnvironment) {
		final DirectoryObject parentDirectoryObject = directoryObject.getParent();

		if (parentDirectoryObject == null) {
			return null;
		}

		DirectoryObject parentDirectoryUnderCvsControl;
		if (clientEnvironment.getAdminReader().hasCvsDirectory(parentDirectoryObject, clientEnvironment.getCvsFileSystem())) {
			parentDirectoryUnderCvsControl = parentDirectoryObject;

			requests.addDirectoryRequest(DirectoryObject.getRoot());
			addDirectoryRequest(parentDirectoryUnderCvsControl, requests);
		}
		else {
			parentDirectoryUnderCvsControl = addDirectoryRequestsUpToLocalDirectory(parentDirectoryObject, requests, requestProcessor, clientEnvironment);
			if (parentDirectoryUnderCvsControl == null) {
				parentDirectoryUnderCvsControl = parentDirectoryObject;
			}
		}

		addDirectoryRequest(directoryObject, requests);
		return parentDirectoryObject;
	}

	private static void addStickyRequest(DirectoryObject directoryObject, Requests requests, ICvsFileSystem cvsFileSystem, IClientEnvironment clientEnvironment) {
		final String tag = clientEnvironment.getAdminReader().getStickyTagForDirectory(directoryObject, cvsFileSystem);
		requests.addStickyRequest(tag);
	}

	private void addDirectoryRequest(DirectoryObject directoryObject, Requests requests) {
		final String repositoryPath = requests.addDirectoryRequest(directoryObject);
		repositoryPathToDirectoryObject.put(repositoryPath, directoryObject);
	}

	private void addRequestsForFile(FileObject fileObject, Requests requests, IClientEnvironment clientEnvironment) throws IOException {
		final DirectoryObject parentDirectory = fileObject.getParent();
		addDirectoryRequest(parentDirectory, requests);
		addStickyRequest(parentDirectory, requests, clientEnvironment.getCvsFileSystem(), clientEnvironment);

		final Entry entry = clientEnvironment.getAdminReader().getEntry(fileObject, clientEnvironment.getCvsFileSystem());
		if (entry != null) {
			requests.addEntryRequest(entry);
		}
		else {
			requests.addIsModifiedRequest(fileObject);
		}
	}
}
