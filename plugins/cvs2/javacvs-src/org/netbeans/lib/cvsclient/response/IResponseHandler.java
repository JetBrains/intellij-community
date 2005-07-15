package org.netbeans.lib.cvsclient.response;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IConnectionStreams;

import java.io.IOException;
import java.util.Date;

/**
 * @author  Thomas Singer
 */
public interface IResponseHandler {

	void processErrorMessageResponse(byte[] message, IResponseServices responseServices);

	void processMessageResponse(byte[] message, IResponseServices responseServices);

	void processMessageTaggedResponse(byte[] message, IResponseServices responseServices);

	void processCheckedInResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException;

	void processNewEntryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServoces, String entryLine, IClientEnvironment clientEnvironment) throws IOException;

	void processSetStaticDirectoryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException;

	void processClearStaticDirectoryResponse(String relativeLocalDirectory, String repositoryDirectoryPath, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException;

	void processSetStickyResponse(String relativeLocalDirectory, String repositoryFilePath, String tag, IClientEnvironment clientEnvironment) throws IOException;

	void processClearStickyResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment) throws IOException;

	void processNotifiedResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment);

	void processRemovedResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException;

	void processRemoveEntryResponse(String relativeLocalDirectory, String repositoryFilePath, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException;

	void processCopyFileResponse(String relativeLocalDirectory, String repositoryFilePath, String newName, IClientEnvironment clientEnvironment) throws IOException;

	void processModTimeResponse(Date modifiedDate, IResponseServices responseServices);

	void processModeResponse(String mode, IResponseServices responseServices);

	void processTemplateResponse(String relativeLocalDirectory, String repositoryFilePath, int length, IClientEnvironment clientEnvironment, IConnectionStreams connectionStreams) throws IOException;

	void processModuleExpansionResponse(String localPath, IResponseServices responseServices);

	void processOkResponse(IResponseServices responseServices);

	void processErrorResponse(byte[] message, IResponseServices responseServices);

	void processUpdatedResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, String mode, int fileLength, IClientEnvironment clientEnvironment, IResponseServices responseServices, IConnectionStreams connectionStreams) throws IOException;

	void processMergedResponse(String relativeLocalDirectory, String repositoryFilePath, String entryLine, String mode, int fileLength, IClientEnvironment clientEnvironment, IResponseServices responseServices, IConnectionStreams connectionStreams) throws IOException;

	void processValidRequestsResponse(String validRequests, IResponseServices responseServices);

        void processBinaryMessageResponse(final int fileLength, final byte[] binaryContent, IResponseServices responseServices);
}
