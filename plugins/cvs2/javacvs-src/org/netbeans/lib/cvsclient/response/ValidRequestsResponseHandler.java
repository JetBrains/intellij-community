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
package org.netbeans.lib.cvsclient.response;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IConnectionStreams;

import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class ValidRequestsResponseHandler extends AbstractResponseHandler {

	// Implemented ============================================================

	public void processErrorMessageResponse(byte[] message, IResponseServices responseServices) {
	}

	public void processMessageResponse(byte[] message, IResponseServices responseServices) {
	}

	public void processMessageTaggedResponse(byte[] message, IResponseServices responseServices) {
	}

	public void processCheckedInResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, IResponseServices responseServices, IClientEnvironment clientEnvironment) {
	}

	public void processNewEntryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServoces, String entryLine, IClientEnvironment clientEnvironment) {
	}

	public void processSetStaticDirectoryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) {
	}

	public void processClearStaticDirectoryResponse(final String relativeLocalDirectory, final String repositoryDirectoryPath, IResponseServices responseServices, IClientEnvironment clientEnvironment) {
	}

	public void processSetStickyResponse(String relativeLocalDirectory, String repositoryFilePath, String tag, IClientEnvironment clientEnvironment) {
	}

	public void processClearStickyResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment) {
	}

	public void processNotifiedResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment) {
	}

	public void processRemovedResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) {
	}

	public void processRemoveEntryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) {
	}

	public void processCopyFileResponse(String relativeLocalDirectory, String repositoryFilePath, String newName, IClientEnvironment clientEnvironment) {
	}

	public void processModTimeResponse(Date modifiedDate, IResponseServices responseServices) {
	}

	public void processModeResponse(String mode, IResponseServices responseServices) {
	}

	public void processTemplateResponse(String relativeLocalDirectory, String repositoryFilePath, int length, IClientEnvironment clientEnvironment, IConnectionStreams connectionStreams) {
	}

	public void processModuleExpansionResponse(String localPath, IResponseServices responseServices) {
	}

	public void processUpdatedResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, String mode, int fileLength, IClientEnvironment clientEnvironment, IResponseServices responseServices, IConnectionStreams connectionStreams) {
	}

	public void processMergedResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, String mode, int fileLength, IClientEnvironment clientEnvironment, IResponseServices responseServices, IConnectionStreams connectionStreams) {
	}

	public void processValidRequestsResponse(String validRequests, IResponseServices responseServices) {
		responseServices.setValidRequests(validRequests);
	}

        public void processBinaryMessageResponse(final int fileLength, final byte[] binaryContent, IResponseServices responseServices) {
        }
}
