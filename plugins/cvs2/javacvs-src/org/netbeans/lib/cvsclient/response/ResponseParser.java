package org.netbeans.lib.cvsclient.response;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.io.StreamUtilities;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class ResponseParser {

	// Fields =================================================================

	private final IResponseHandler responseProcessor;
	private final StreamUtilities myStreamUtilities;
        @NonNls public static final String PREFIX_TO_REMOVE = "-f ";

  // Setup ==================================================================

	public ResponseParser(IResponseHandler responseProcessor, String charset) {
		BugLog.getInstance().assertNotNull(responseProcessor);
		BugLog.getInstance().assertNotNull(charset);

		this.responseProcessor = responseProcessor;
		myStreamUtilities = new StreamUtilities(charset);
	}

	// Implemented ============================================================

	@SuppressWarnings({"HardCodedStringLiteral"})
        public Boolean processResponse(String responseName, IConnectionStreams connectionStreams, IResponseServices responseServices, IClientEnvironment clientEnvironment) throws IOException {
    InputStream loggedInputStream = connectionStreams.getLoggedInputStream();
    if (responseName.equalsIgnoreCase("E")) {
      final byte[] line = StreamUtilities.readLineBytes(loggedInputStream);
            responseProcessor.processErrorMessageResponse(prepareMessageAccordingToScr39148(line), responseServices);
      return null;
    }
    else if (responseName.equalsIgnoreCase("M")) {
      final byte[] line = StreamUtilities.readLineBytes(loggedInputStream);
      responseProcessor.processMessageResponse(prepareMessageAccordingToScr39148(line), responseServices);
      return null;
    }

                else if (responseName.equalsIgnoreCase("MBinary")) {
      final String fileLengthString = myStreamUtilities.readLine(loggedInputStream);
                        try {
                          int fileLength = Integer.parseInt(fileLengthString);

                          responseProcessor.processBinaryMessageResponse(fileLength, readFromStream(connectionStreams, fileLength), responseServices);
                        }
                        catch (NumberFormatException e) {
                          //ignore
                        }
                  return null;
    }

    else if (responseName.equalsIgnoreCase("MT")) {
      final byte[] text = StreamUtilities.readLineBytes(loggedInputStream);
      responseProcessor.processMessageTaggedResponse(text, responseServices);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Updated")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String entryLine = myStreamUtilities.readLine(loggedInputStream);
      final String mode = myStreamUtilities.readLine(loggedInputStream);
      final String fileLengthString = myStreamUtilities.readLine(loggedInputStream);

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
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String entryLine = myStreamUtilities.readLine(loggedInputStream);
      final String mode = myStreamUtilities.readLine(loggedInputStream);
      final String fileLengthString = myStreamUtilities.readLine(loggedInputStream);

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
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String entryLine = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processCheckedInResponse(relativeLocalDirectory, repositoryFilePath, entryLine, responseServices, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("New-entry")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String entryLine = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processNewEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, entryLine, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Set-static-directory")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processSetStaticDirectoryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Clear-static-directory")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryDirectoryPath = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processClearStaticDirectoryResponse(relativeLocalDirectory, repositoryDirectoryPath, responseServices, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Set-sticky")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String tag = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processSetStickyResponse(relativeLocalDirectory, repositoryFilePath, tag, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Clear-sticky")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processClearStickyResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Notified")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath =  myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processNotifiedResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Removed")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFileName = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processRemovedResponse(relativeLocalDirectory, repositoryFileName, responseServices, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Remove-entry")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processRemoveEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Copy-file")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String newName = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processCopyFileResponse(relativeLocalDirectory, repositoryFilePath, newName, clientEnvironment);
      return null;
    }
    else if (responseName.equalsIgnoreCase("Mod-time")) {
      final String dateString = myStreamUtilities.readLine(loggedInputStream);
      try {
        final Date modifiedDate = ResponseUtils.parseDateString(dateString);
        responseProcessor.processModTimeResponse(modifiedDate, responseServices);
      }
      catch (Exception ex) {
        BugLog.getInstance().showException(ex);
      }
      return null;
    }
    else if (responseName.equalsIgnoreCase("Mode")) {
      final String mode = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processModeResponse(mode, responseServices);
            return null;
    }
    else if (responseName.equalsIgnoreCase("Template")) {
      final String relativeLocalDirectory = myStreamUtilities.readLine(loggedInputStream);
      final String repositoryFilePath = myStreamUtilities.readLine(loggedInputStream);
      final String lengthString = myStreamUtilities.readLine(loggedInputStream);
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
      final String localPath = myStreamUtilities.readLine(loggedInputStream);
      responseProcessor.processModuleExpansionResponse(localPath, responseServices);
      return null;
    }
    else if (responseName.equalsIgnoreCase("ok")) {
      responseProcessor.processOkResponse(responseServices);
      return Boolean.TRUE;
    }
    else if (responseName.equalsIgnoreCase("error")) {
      final byte[] message = StreamUtilities.readLineBytes(loggedInputStream);
      responseProcessor.processErrorResponse(message, responseServices);
      return Boolean.FALSE;
    }
		if (responseName.equalsIgnoreCase("Valid-requests")) {
			final String validRequests = myStreamUtilities.readLine(loggedInputStream);
			responseProcessor.processValidRequestsResponse(validRequests, responseServices);
			return null;
		}
		else {
			throw new IOException("Unhandled response: " + responseName + ".");
		}
	}

  private byte[] readFromStream(final IConnectionStreams connectionStreams, final int fileLength) throws IOException {
    final byte[] buffer = new byte[128 * 1024];
    int read = 0;
    final ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
    while (read < fileLength) {
      final int readBytes = connectionStreams.getInputStream().read(buffer, 0, fileLength);
      bufferStream.write(buffer, 0, readBytes);
      read += readBytes;
    }
    return bufferStream.toByteArray();
  }

  private byte[] prepareMessageAccordingToScr39148(byte[] line) {
    if (line.length < 3) return line;
    if (startsWith(PREFIX_TO_REMOVE, line)) {
      final byte[] result = new byte[line.length - 3];
      System.arraycopy(line, 3, result, 0, result.length);
      return result;
    }
    return line;
    }

  private boolean startsWith(final String s, final byte[] line) {
    for (int i = 0; i < s.length(); i++) {
      if (line[i] == s.charAt(i)) continue;
      return false;
    }
    return true;
  }

}
