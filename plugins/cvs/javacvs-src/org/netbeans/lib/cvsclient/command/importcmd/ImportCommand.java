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
package org.netbeans.lib.cvsclient.command.importcmd;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.CommandUtils;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.DirectoryRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.netbeans.lib.cvsclient.util.SimpleStringPattern;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The import command imports local directory structures into the repository.
 *
 * @author  Thomas Singer
 */
public final class ImportCommand extends Command {

	// Fields =================================================================

	private final Map<SimpleStringPattern, KeywordSubstitution> wrapperMap = new HashMap();
	private String logMessage;
	private String module;
	private String releaseTag;
	private String vendorBranch;
	private String vendorTag;
	private KeywordSubstitution keywordSubstitutionOption;

	// Setup ==================================================================

	public ImportCommand() {}

	// Implemented ============================================================

	public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                  AuthenticationException {
		// check necessary fields
		BugLog.getInstance().assertNotNull(getModule());
		BugLog.getInstance().assertNotNull(getReleaseTag());
		BugLog.getInstance().assertNotNull(getVendorTag());

		final Requests requests;
		requests = new Requests(CommandRequest.IMPORT, clientEnvironment);
		requests.addArgumentRequest(getVendorBranchNotNull(), "-b");
		requests.addMessageRequests(CommandUtils.getMessageNotNull(getLogMessage()));
		requests.addArgumentRequest(getKeywordSubstitutionOption(), "-k");

		addWrapperRequests(requests, this.wrapperMap);

		requests.addArgumentRequest(getModule());
		requests.addArgumentRequest(getVendorTag());
		requests.addArgumentRequest(getReleaseTag());

		final File rootDirectory = clientEnvironment.getCvsFileSystem().getLocalFileSystem().getRootDirectory();
		addFileRequests(rootDirectory, requests, requestProcessor, clientEnvironment);

		// This is necessary when importing a directory structure with CVS directories.
		// If requests.addLocalPathDirectoryRequest(); would be used, the repository path
		// would be used from the CVS folders.
		requests.addRequest(new DirectoryRequest(".", getRepositoryRoot(clientEnvironment)));

		return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
	}

	public void resetCvsCommand() {
		super.resetCvsCommand();
		setLogMessage(null);
		setModule(null);
		setReleaseTag(null);
		setVendorTag(null);
		setVendorBranch(null);
		setKeywordSubstitutionOption(null);
		if (wrapperMap != null) {
			wrapperMap.clear();
		}
	}

	public String getCvsCommandLine() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder("import ");
		cvsArguments.append(getCvsArguments());

		cvsArguments.append(' ');
		cvsArguments.append(getModule());

		cvsArguments.append(' ');
		cvsArguments.append(getVendorTag());

		cvsArguments.append(' ');
		cvsArguments.append(getReleaseTag());
		return cvsArguments.toString();
	}

	// Accessing ==============================================================

	public void addWrapper(String filenamePattern, KeywordSubstitution keywordSubstitutionOptions) {
		BugLog.getInstance().assertNotNull(keywordSubstitutionOptions);

		wrapperMap.put(new SimpleStringPattern(filenamePattern), keywordSubstitutionOptions);
	}

	private KeywordSubstitution getKeywordSubstitutionOption() {
		return keywordSubstitutionOption;
	}

	public void setKeywordSubstitutionOption(KeywordSubstitution keywordSubstitutionOption) {
		this.keywordSubstitutionOption = keywordSubstitutionOption;
	}

	private String getReleaseTag() {
		return releaseTag;
	}

	public void setReleaseTag(String releaseTag) {
		this.releaseTag = getTrimmedString(releaseTag);
	}

	private String getLogMessage() {
		return logMessage;
	}

	public void setLogMessage(String logMessage) {
		this.logMessage = logMessage;
	}

	private String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = getTrimmedString(module);
	}

	private String getVendorBranch() {
		return vendorBranch;
	}

	/**
	 * Returns the vendor branch.
	 * If not set, then 1.1.1 is returned.
	 */
	private String getVendorBranchNotNull() {
		if (vendorBranch == null) {
			return "1.1.1";
		}

		return vendorBranch;
	}

	/**
	 * Sets the vendor branch.
	 * If null is set, the default branch 1.1.1 is used automatically.
	 */
	public void setVendorBranch(String vendorBranch) {
		this.vendorBranch = getTrimmedString(vendorBranch);
	}

	private String getVendorTag() {
		return vendorTag;
	}

	public void setVendorTag(String vendorTag) {
		this.vendorTag = getTrimmedString(vendorTag);
	}

	// Utils ==================================================================

	private String getCvsArguments() {
		@NonNls final StringBuilder cvsArguments = new StringBuilder();
		cvsArguments.append("-m \"");
		cvsArguments.append(CommandUtils.getMessageNotNull(getLogMessage()));
		cvsArguments.append("\" ");

		if (getKeywordSubstitutionOption() != null) {
			cvsArguments.append("-k");
			cvsArguments.append(getKeywordSubstitutionOption().toString());
			cvsArguments.append(" ");
		}
		if (getVendorBranch() != null) {
			cvsArguments.append("-b ");
			cvsArguments.append(getVendorBranch());
			cvsArguments.append(" ");
		}
		if (wrapperMap.size() > 0) {
			for (final SimpleStringPattern pattern : wrapperMap.keySet()) {
				final KeywordSubstitution keywordSubstitutionOptions = wrapperMap.get(pattern);
				cvsArguments.append("-W ");
				cvsArguments.append(pattern.toString());
				cvsArguments.append(" -k '");
				cvsArguments.append(keywordSubstitutionOptions.toString());
				cvsArguments.append("' ");
			}
		}
		return cvsArguments.toString();
	}

	/**
	 * Adds requests for specified wrappers to the specified requestList.
	 */
	private static void addWrapperRequests(Requests requests, Map<SimpleStringPattern, KeywordSubstitution> wrapperMap) {
		// override the server's ignore list
		requests.addArgumentRequest("-I !");

		for (final SimpleStringPattern pattern : wrapperMap.keySet()) {
			final KeywordSubstitution keywordSubstitutionOptions = wrapperMap.get(pattern);

			@NonNls final StringBuilder buffer = new StringBuilder();
			buffer.append(pattern.toString());
			buffer.append(" -k '");
			buffer.append(keywordSubstitutionOptions.toString());
			buffer.append("'");

			requests.addArgumentRequest("-W");
			requests.addArgumentRequest(buffer.toString());
		}
	}

	/**
	 * Adds recursively all request for files and directories in the specified
	 * directory to the specified requestList.
	 */
	private void addFileRequests(File directory, Requests requests, IRequestProcessor requestProcessor, IClientEnvironment clientEnvironment) {
		final DirectoryObject directoryObject = clientEnvironment.getCvsFileSystem().getLocalFileSystem().getDirectoryObject(directory);

		final String relativePath = directoryObject.toUnixPath();
		String repository = getRepositoryRoot(clientEnvironment);
		if (!relativePath.equals(".")) {
			repository += '/' + relativePath;
		}
		requests.addRequest(new DirectoryRequest(relativePath, repository));

		final File[] files = directory.listFiles();
		if (files == null) {
			return;
		}

		final List<File> subdirectories = new ArrayList();

		for (final File file : files) {
			if (file.isDirectory()) {
				final DirectoryObject subDirObject = clientEnvironment.getCvsFileSystem().getLocalFileSystem().getDirectoryObject(file);

				if (clientEnvironment.getIgnoreFileFilter().shouldBeIgnored(subDirObject, clientEnvironment.getCvsFileSystem())) {
					continue;
				}

				subdirectories.add(file);
			}
			else {
				final FileObject fileObject = clientEnvironment.getCvsFileSystem().getLocalFileSystem().getFileObject(file);

				if (clientEnvironment.getIgnoreFileFilter().shouldBeIgnored(fileObject, clientEnvironment.getCvsFileSystem())) {
					continue;
				}

				final KeywordSubstitution keywordSubstMode = getKeywordSubstMode(file.getName());
				final boolean writable = clientEnvironment.getLocalFileReader().isWritable(fileObject, clientEnvironment.getCvsFileSystem());
                                if (keywordSubstMode != null) {
                                        requests.addKoptRequest(keywordSubstMode);
                                }
				requests.addModifiedRequest(fileObject, keywordSubstMode == KeywordSubstitution.BINARY, writable);
			}
		}

		for (final File subdirectory : subdirectories) {
			addFileRequests(subdirectory, requests, requestProcessor, clientEnvironment);
		}
	}

	/**
	 * Returns the used root path in the repository.
	 * It's built from the repository stored in the clientService and the
	 * module.
	 */
	private String getRepositoryRoot(IClientEnvironment clientEnvironment) {
		return clientEnvironment.getCvsRoot().getRepositoryPath() + '/' + getModule();
	}

	/**
	 * Returns true, if the file for the specified filename should be treated as
	 * a binary file.
	 *
	 * The information comes from the wrapper map and the set keywordsubstitution.
	 */
	private KeywordSubstitution getKeywordSubstMode(String fileName) {
		KeywordSubstitution keywordSubstMode = getKeywordSubstitutionOption();

		for (final SimpleStringPattern pattern : wrapperMap.keySet()) {
			if (pattern.doesMatch(fileName)) {
				keywordSubstMode = wrapperMap.get(pattern);
				break;
			}
		}

		return keywordSubstMode;
	}
}
