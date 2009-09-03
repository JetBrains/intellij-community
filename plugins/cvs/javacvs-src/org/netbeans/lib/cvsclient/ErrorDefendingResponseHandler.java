package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.response.IResponseHandler;
import org.netbeans.lib.cvsclient.response.IResponseServices;

import java.io.IOException;
import java.util.Date;

public class ErrorDefendingResponseHandler implements IResponseHandler {
  private static final int MAX_ERRORS_NUM = 50;
  private final long myErrorStreamTimeout;
  private final static long ourOkConstant = -1;
  private long myErrorStreamStart;
  private int myConsequentErrorsCount;
  private final IResponseHandler myHandler;

  public ErrorDefendingResponseHandler(final long errorStreamTimeout, final IResponseHandler handler) {
    myErrorStreamTimeout = errorStreamTimeout;
    myHandler = handler;
    myErrorStreamStart = ourOkConstant;
    myConsequentErrorsCount = 0;
  }

  private void onError() {
    ++ myConsequentErrorsCount;
    if (ourOkConstant == myErrorStreamStart) {
      myErrorStreamStart = System.currentTimeMillis();
    }
  }

  private void onOk() {
    myErrorStreamStart = ourOkConstant;
    myConsequentErrorsCount = 0;
  }

  public boolean interrupt() {
    return (myConsequentErrorsCount >= MAX_ERRORS_NUM) ||
           ((myErrorStreamTimeout > 0) && (ourOkConstant != myErrorStreamStart) && ((System.currentTimeMillis() - myErrorStreamStart) >= myErrorStreamTimeout));
  }

  public void processErrorMessageResponse(final byte[] message, final IResponseServices responseServices) {
    myHandler.processErrorMessageResponse(message, responseServices);
    onError();
  }

  public void processMessageResponse(final byte[] message, final IResponseServices responseServices) {
    myHandler.processMessageResponse(message, responseServices);
    onOk();
  }

  public void processMessageTaggedResponse(final byte[] message, final IResponseServices responseServices) {
    myHandler.processMessageTaggedResponse(message, responseServices);
    onOk();
  }

  public void processCheckedInResponse(final String relativeLocalDirectory,
                                       final String repositoryFilePath,
                                       final String entryLine,
                                       final IResponseServices responseServices, final IClientEnvironment clientEnvironment)
      throws IOException {
    myHandler.processCheckedInResponse(relativeLocalDirectory, repositoryFilePath, entryLine, responseServices, clientEnvironment);
    onOk();
  }

  public void processNewEntryResponse(final String relativeLocalDirectory, final String repositoryFilePath, final IResponseServices responseServoces,
                                      final String entryLine,
                                      final IClientEnvironment clientEnvironment) throws IOException {
    myHandler.processNewEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServoces, entryLine, clientEnvironment);
    onOk();
  }

  public void processSetStaticDirectoryResponse(final String relativeLocalDirectory,
                                                final String repositoryFilePath,
                                                final IResponseServices responseServices, final IClientEnvironment clientEnvironment)
      throws IOException {
    myHandler.processSetStaticDirectoryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
    onOk();
  }

  public void processClearStaticDirectoryResponse(final String relativeLocalDirectory,
                                                  final String repositoryDirectoryPath,
                                                  final IResponseServices responseServices, final IClientEnvironment clientEnvironment)
      throws IOException {
    myHandler.processClearStaticDirectoryResponse(relativeLocalDirectory, repositoryDirectoryPath, responseServices, clientEnvironment);
    onOk();
  }

  public void processSetStickyResponse(final String relativeLocalDirectory,
                                       final String repositoryFilePath,
                                       final String tag,
                                       final IClientEnvironment clientEnvironment) throws IOException {
    myHandler.processSetStickyResponse(relativeLocalDirectory, repositoryFilePath, tag, clientEnvironment);
    onOk();
  }

  public void processClearStickyResponse(final String relativeLocalDirectory, final String repositoryFilePath, final IClientEnvironment clientEnvironment)
      throws IOException {
    myHandler.processClearStickyResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
    onOk();
  }

  public void processNotifiedResponse(final String relativeLocalDirectory, final String repositoryFilePath, final IClientEnvironment clientEnvironment) {
    myHandler.processNotifiedResponse(relativeLocalDirectory, repositoryFilePath, clientEnvironment);
    onOk();
  }

  public void processRemovedResponse(final String relativeLocalDirectory, final String repositoryFilePath, final IResponseServices responseServices,
                                     final IClientEnvironment clientEnvironment) throws IOException {
    myHandler.processRemovedResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
    onOk();
  }

  public void processRemoveEntryResponse(final String relativeLocalDirectory, final String repositoryFilePath, final IResponseServices responseServices,
                                         final IClientEnvironment clientEnvironment) throws IOException {
    myHandler.processRemoveEntryResponse(relativeLocalDirectory, repositoryFilePath, responseServices, clientEnvironment);
    onOk();
  }

  public void processCopyFileResponse(final String relativeLocalDirectory,
                                      final String repositoryFilePath,
                                      final String newName,
                                      final IClientEnvironment clientEnvironment) throws IOException {
    myHandler.processCopyFileResponse(relativeLocalDirectory, repositoryFilePath, newName, clientEnvironment);
    onOk();
  }

  public void processModTimeResponse(final Date modifiedDate, final IResponseServices responseServices) {
    myHandler.processModTimeResponse(modifiedDate, responseServices);
    onOk();
  }

  public void processModeResponse(final String mode, final IResponseServices responseServices) {
    myHandler.processModeResponse(mode, responseServices);
    onOk();
  }

  public void processTemplateResponse(final String relativeLocalDirectory,
                                      final String repositoryFilePath,
                                      final int length,
                                      final IClientEnvironment clientEnvironment, final IConnectionStreams connectionStreams)
      throws IOException {
    myHandler.processTemplateResponse(relativeLocalDirectory, repositoryFilePath, length, clientEnvironment, connectionStreams);
    onOk();
  }

  public void processModuleExpansionResponse(final String localPath, final IResponseServices responseServices) {
    myHandler.processModuleExpansionResponse(localPath, responseServices);
    onOk();
  }

  public void processOkResponse(final IResponseServices responseServices) {
    myHandler.processOkResponse(responseServices);
    onOk();
  }

  public void processErrorResponse(final byte[] message, final IResponseServices responseServices) {
    myHandler.processErrorResponse(message, responseServices);
    onError();
  }

  public void processUpdatedResponse(final String relativeLocalDirectory,
                                     final String repositoryFilePath,
                                     final String entryLine,
                                     final String mode,
                                     final int fileLength,
                                     final IClientEnvironment clientEnvironment, final IResponseServices responseServices,
                                     final IConnectionStreams connectionStreams) throws IOException {
    myHandler.processUpdatedResponse(relativeLocalDirectory, repositoryFilePath, entryLine, mode, fileLength, clientEnvironment,
                                     responseServices, connectionStreams);
    onOk();
  }

  public void processMergedResponse(final String relativeLocalDirectory,
                                    final String repositoryFilePath,
                                    final String entryLine,
                                    final String mode,
                                    final int fileLength,
                                    final IClientEnvironment clientEnvironment, final IResponseServices responseServices,
                                    final IConnectionStreams connectionStreams) throws IOException {
    myHandler.processMergedResponse(relativeLocalDirectory, repositoryFilePath, entryLine, mode, fileLength, clientEnvironment,
                                    responseServices, connectionStreams);
    onOk();
  }

  public void processValidRequestsResponse(final String validRequests, final IResponseServices responseServices) {
    myHandler.processValidRequestsResponse(validRequests, responseServices);
    onOk();
  }

  public void processBinaryMessageResponse(final int fileLength, final byte[] binaryContent, final IResponseServices responseServices) {
    myHandler.processBinaryMessageResponse(fileLength, binaryContent, responseServices);
    onOk();
  }
}
