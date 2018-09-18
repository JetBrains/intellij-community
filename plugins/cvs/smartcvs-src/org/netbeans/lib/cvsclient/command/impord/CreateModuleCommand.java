// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.command.impord;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileUtils;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.DirectoryRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class CreateModuleCommand extends Command {

	// Fields =================================================================

	private String module;

	// Setup ==================================================================

	public CreateModuleCommand() {
	}

	// Implemented ============================================================

	@Override
        public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager, ICvsListenerRegistry listenerRegistry, IClientEnvironment clientEnvironment, IProgressViewer progressViewer) throws CommandException,
                                                                                                                                                                                                                  AuthenticationException {
		// check necessary fields
		BugLog.getInstance().assertNotNull(module);

		final String repositoryRoot = FileUtils.removeTrailingSlash(clientEnvironment.getCvsRoot().getRepositoryPath()) + '/' + module;

		final Requests requests = new Requests(CommandRequest.IMPORT, clientEnvironment);
		requests.addArgumentRequest("-b");
		requests.addArgumentRequest("1.1.1");
		requests.addMessageRequests("Create module");
		requests.addArgumentRequest(module);
		requests.addArgumentRequest("vendor-tag");
		requests.addArgumentRequest("release-tag");
		requests.addRequest(new DirectoryRequest(".", repositoryRoot));

		if (!requestProcessor.processRequests(requests, new DummyRequestsProgressHandler())) {
			return false;
		}

		try {
			createCvsDirectory(clientEnvironment, repositoryRoot);
		}
		catch (IOException ex) {
			throw new IOCommandException(ex);
		}

		return true;
	}

	@Override
        public void resetCvsCommand() {
		super.resetCvsCommand();
		setModule(null);
	}

	@Override
        public String getCvsCommandLine() {
		return "import " + module;
	}

	// Accessing ==============================================================

	public void setModule(String module) {
		this.module = getTrimmedString(module);
	}

	// Utils ==================================================================

	private void createCvsDirectory(IClientEnvironment clientEnvironment, String repositoryRoot) throws IOException {
		clientEnvironment.getAdminWriter().ensureCvsDirectory(DirectoryObject.getRoot(), repositoryRoot, clientEnvironment.getCvsRoot(), clientEnvironment.getCvsFileSystem());
	}
}
