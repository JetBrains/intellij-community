package org.netbeans.lib.cvsclient.response;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.io.StreamUtilities;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class ResponseParser {

	// Fields =================================================================

	private final IResponseHandler responseProcessor;

	// Setup ==================================================================

	public ResponseParser(IResponseHandler responseProcessor) {
		BugLog.getInstance().assertNotNull(responseProcessor);

		this.responseProcessor = responseProcessor;
	}

	// Implemented ============================================================

	public Boolean processResponse(String responseName, IConnectionStreams connectionStreams, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException {
		if (responseName.equalsIgnoreCase("E")) {
			final String line = StreamUtilities.readLine(connectionStreams.getLoggedReader());
            responseProcessor.processErrorMessageResponse(prepareMessageAccordingToSrc39148(line), responseServices);
			return null;
		}
		else if (responseName.equalsIgnoreCase("M")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String line = StreamUtilities.readLine(reader);
			responseProcessor.processMessageResponse(prepareMessageAccordingToSrc39148(line), responseServices);
			return null;
		}
		else if (responseName.equalsIgnoreCase("MT")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String text = StreamUtilities.readLine(reader);
			responseProcessor.processMessageTaggedResponse(text, responseServices);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Updated")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String entryLine = StreamUtilities.readLine(reader);
			final String mode = StreamUtilities.readLine(reader);
			final String fileLengthString = StreamUtilities.readLine(reader);

			final int fileLength;
			try {
				fileLength = Integer.parseInt(fileLengthString);
				responseProcessor.processUpdatedResponse(relativeLocalDirectory, repositoryFilePath, entryLine, mode, fileLength, clientEnvironment, responseServices, connectionStreams);
			}
			catch (NumberFormatException ex) {
				// ignore
			}
			return null;
		}
		else if (responseName.equalsIgnoreCase("Merged")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String entryLine = StreamUtilities.readLine(reader);
			final String mode = StreamUtilities.readLine(reader);
			final String fileLengthString = StreamUtilities.readLine(reader);

			final int fileLength;
			try {
				fileLength = Integer.parseInt(fileLengthString);
				responseProcessor.processMergedResponse(relativeLocalDirectory, repositoryFilePath, entryLine, mode, fileLength, clientEnvironment, responseServices, connectionStreams);
			}
			catch (NumberFormatException ex) {
				// ignore
			}
			return null;
		}
		else if (responseName.equalsIgnoreCase("Checked-in")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String entryLine = StreamUtilities.readLine(reader);
			responseProcessor.processCheckedInResponse(relativeLocalDirectory, repositoryFilePath, entryLine, responseServices, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("New-entry")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String entryLine = StreamUtilities.readLine(reader);
			responseProcessor.processNewEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, entryLine, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Set-static-directory")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			responseProcessor.processSetStaticDirectoryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Clear-static-directory")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryDirectoryPath = StreamUtilities.readLine(reader);
			responseProcessor.processClearStaticDirectoryResponse(relativeLocalDirectory, repositoryDirectoryPath, responseServices, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Set-sticky")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String tag = StreamUtilities.readLine(reader);
			responseProcessor.processSetStickyResponse(relativeLocalDirectory, repositoryFilePath, tag, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Clear-sticky")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			responseProcessor.processClearStickyResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Notified")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath =  StreamUtilities.readLine(reader);
			responseProcessor.processNotifiedResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Removed")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFileName = StreamUtilities.readLine(reader);
			responseProcessor.processRemovedResponse(relativeLocalDirectory, repositoryFileName, responseServices, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Remove-entry")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			responseProcessor.processRemoveEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Copy-file")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String newName = StreamUtilities.readLine(reader);
			responseProcessor.processCopyFileResponse(relativeLocalDirectory, repositoryFilePath, newName, clientEnvironment);
			return null;
		}
		else if (responseName.equalsIgnoreCase("Mod-time")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String dateString = StreamUtilities.readLine(reader);
			try {
				final Date modifiedDate = ResponseUtils.parseDateString(dateString);
				responseProcessor.processModTimeResponse(modifiedDate, responseServices);
			}
			catch (ParseException ex) {
				BugLog.getInstance().showException(ex);
			}
			return null;
		}
		else if (responseName.equalsIgnoreCase("Mode")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String mode = StreamUtilities.readLine(reader);
			responseProcessor.processModeResponse(mode, responseServices);
            return null;
		}
		else if (responseName.equalsIgnoreCase("Template")) {
			final Reader reader = connectionStreams.getLoggedReader();
			final String relativeLocalDirectory = StreamUtilities.readLine(reader);
			final String repositoryFilePath = StreamUtilities.readLine(reader);
			final String lengthString = StreamUtilities.readLine(reader);
			final int length;
			try {
				// the following line can be sent by the server, when the template file is not available on the server
				// "E cvs server: Couldn't open rcsinfo template file /cvsroot/geotools/CVSROOT/gtTemplate: No such file or directory"
				length = Integer.parseInt(lengthString);
				responseProcessor.processTemplateResponse(relativeLocalDirectory, repositoryFilePath, length, clientEnvironment, connectionStreams);
			}
			catch (NumberFormatException ex) {
				// ignore
			}
			return null;
		}
		else if (responseName.equalsIgnoreCase("Module-expansion")) {
			final String localPath = StreamUtilities.readLine(connectionStreams.getLoggedReader());
			responseProcessor.processModuleExpansionResponse(localPath, responseServices);
			return null;
		}
		else if (responseName.equalsIgnoreCase("ok")) {
			responseProcessor.processOkResponse(responseServices);
			return Boolean.TRUE;
		}
		else if (responseName.equalsIgnoreCase("error")) {
			final String message = StreamUtilities.readLine(connectionStreams.getLoggedReader());
			responseProcessor.processErrorResponse(message, responseServices);
			return Boolean.FALSE;
		}
		if (responseName.equalsIgnoreCase("Valid-requests")) {
			final String validRequests = StreamUtilities.readLine(connectionStreams.getLoggedReader());
			responseProcessor.processValidRequestsResponse(validRequests, responseServices);
			return null;
		}
		else {
			throw new IOException("Unhandled response: " + responseName + ".");
		}
	}

    private String prepareMessageAccordingToSrc39148(String line) {
        if (line.startsWith("-f ")) {
            line = line.substring(3);
        }
        return line;
    }

}
