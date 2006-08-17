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
package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IGlobalOptions;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileDetails;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.io.IStreamLogger;
import org.netbeans.lib.cvsclient.progress.sending.IRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.*;
import org.netbeans.lib.cvsclient.response.DefaultResponseHandler;
import org.netbeans.lib.cvsclient.response.IResponseHandler;
import org.netbeans.lib.cvsclient.response.ResponseParser;
import org.netbeans.lib.cvsclient.response.ValidRequestsResponseHandler;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Thomas Singer
 */
public final class RequestProcessor implements IRequestProcessor {

  // Fields =================================================================

  private final IGlobalOptions globalOptions;
  private final IClientEnvironment clientEnvironment;
  private final ResponseService responseServices;
  private final IStreamLogger streamLogger;
  private final ICvsCommandStopper commandStopper;
  @NonNls private static final String OS_NAME_PROPERTY = "os.name";
  @NonNls private static final String WINDOWS_PREFIX = "Windows";
  @NonNls private static final String CASE_REQUEST = "Case";
  @NonNls private static final String CVS_PASS_ENV_VARS_PROPERTY = "cvs.pass.env.vars";
  @NonNls private static final String NO = "no";

  // Setup ==================================================================

  public RequestProcessor(IClientEnvironment clientEnvironment,
                          IGlobalOptions globalOptions,
                          IEventSender eventSender,
                          IStreamLogger streamLogger,
                          ICvsCommandStopper commandStopper) {
    BugLog.getInstance().assertNotNull(globalOptions);
    BugLog.getInstance().assertNotNull(clientEnvironment);
    BugLog.getInstance().assertNotNull(eventSender);
    BugLog.getInstance().assertNotNull(streamLogger);
    BugLog.getInstance().assertNotNull(commandStopper);

    this.globalOptions = globalOptions;
    this.clientEnvironment = clientEnvironment;
    this.responseServices = new ResponseService(eventSender);
    this.streamLogger = streamLogger;
    this.commandStopper = commandStopper;
  }

  // Implemented ============================================================

  public boolean processRequests(Requests requests, IRequestsProgressHandler communicationProgressHandler) throws CommandException {
    IConnectionStreams connectionStreams = openConnection();
    try {
      return processRequests(requests, connectionStreams, communicationProgressHandler);
    }
    finally {
      connectionStreams.close();
    }
  }

  // Utils ==================================================================

  private IConnectionStreams openConnection() throws CommandException {
    try {
      clientEnvironment.getConnection().open(streamLogger);
    }
    catch (AuthenticationException ex) {
      throw new CommandException(ex, JavaCvsSrcBundle.message("could.not.establish.connection.error.message"));
    }

    ConnectionStreams connectionStreams =
      new ConnectionStreams(clientEnvironment.getConnection(), streamLogger, clientEnvironment.getCharset());
    boolean exception = true;
    try {
      updateValidRequests(connectionStreams);

      sendRequest(new RootRequest(clientEnvironment.getConnection().getRepository()), connectionStreams);
      sendSetRequests(globalOptions, connectionStreams);
      // Handle gzip-compression
      if (globalOptions.isUseGzip() && isValidRequest(GzipStreamRequest.REQUEST)) {
        sendRequest(new GzipStreamRequest(), connectionStreams);

        connectionStreams.setGzipped();
      }

      //TODO: set variables

      sendRequest(new ValidResponsesRequest(), connectionStreams);

      sendRequest(new UseUnchangedRequest(), connectionStreams);
      sendGlobalOptionRequests(globalOptions, connectionStreams);

      if (System.getProperty(OS_NAME_PROPERTY).startsWith(WINDOWS_PREFIX) && isValidRequest(CASE_REQUEST)) {
        sendRequest(new CaseRequest(), connectionStreams);
      }

      exception = false;
      return connectionStreams;
    }
    catch (IOException ex) {
      BugLog.getInstance().showException(ex);
      throw new IOCommandException(ex);
    }
    finally {
      if (exception) {
        connectionStreams.close();
      }
    }
  }

  private void sendSetRequests(IGlobalOptions globalOptions, ConnectionStreams connectionStreams)
    throws CommandAbortedException, IOException {
    Map envVariables = globalOptions.getEnvVariables();
    if (envVariables == null) {
      return;
    }
    for (Iterator iterator = envVariables.keySet().iterator(); iterator.hasNext();) {
      String varName = (String)iterator.next();
      String varValue = (String)envVariables.get(varName);
      sendRequest(new SetRequest(varName, varValue), connectionStreams);
    }
  }

  private boolean processRequests(Requests requests,
                                  IConnectionStreams connectionStreams,
                                  IRequestsProgressHandler communicationProgressHandler)
    throws CommandAbortedException, IOCommandException {
    BugLog.getInstance().assertNotNull(requests);

    try {
      sendRequests(requests, connectionStreams, communicationProgressHandler);

      sendRequest(requests.getResponseExpectingRequest(), connectionStreams);
      connectionStreams.flushForReading();

      return handleResponses(connectionStreams, new DefaultResponseHandler());
    }
    catch (IOException ex) {
      throw new IOCommandException(ex);
    }
  }

  private void sendRequests(Requests requests, IConnectionStreams connectionStreams, IRequestsProgressHandler communicationProgressHandler)
    throws CommandAbortedException, IOException {
    for (Iterator it = requests.getRequests().iterator(); it.hasNext();) {
      final IRequest request = (IRequest)it.next();

      sendRequest(request, connectionStreams);

      final FileDetails fileDetails = request.getFileForTransmission();
      if (fileDetails != null) {
        sendFile(fileDetails, connectionStreams);
      }

      communicationProgressHandler.requestSent(request);
    }
  }

  private void updateValidRequests(IConnectionStreams connectionStreams) throws CommandException, IOException {
    sendRequest(new ValidRequestsRequest(), connectionStreams);
    connectionStreams.flushForReading();
    handleResponses(connectionStreams, new ValidRequestsResponseHandler());

    if (responseServices.getValidRequests() == null) {
      throw new ValidRequestsExpectedException();
    }
  }

  private void sendGlobalOptionRequests(IGlobalOptions globalOptions, IConnectionStreams connectionStreams)
    throws CommandAbortedException, IOException {
    if (!isValidRequest(GlobalOptionRequest.REQUEST)) {
      return;
    }

    if (globalOptions.isCheckedOutFilesReadOnly()) {
      sendRequest(new GlobalOptionRequest("-r"), connectionStreams);
    }
    if (globalOptions.isDoNoChanges()) {
      sendRequest(new GlobalOptionRequest("-n"), connectionStreams);
    }
    if (globalOptions.isNoHistoryLogging()) {
      sendRequest(new GlobalOptionRequest("-l"), connectionStreams);
    }
    if (globalOptions.isSomeQuiet()) {
      sendRequest(new GlobalOptionRequest("-q"), connectionStreams);
    }
  }

  private boolean isValidRequest(String request) {
    return responseServices.getValidRequests().indexOf(request) >= 0;
  }

  private void sendRequest(IRequest request, IConnectionStreams connectionStreams) throws CommandAbortedException, IOException {
    checkCanceled();
    connectionStreams.getLoggedWriter().write(request.getRequestString());
  }

  private void checkCanceled() throws CommandAbortedException {
    if (commandStopper.isAborted()) {
      throw new CommandAbortedException();
    }
  }

  private boolean handleResponses(IConnectionStreams connectionStreams, IResponseHandler responseHandler)
    throws CommandAbortedException, IOException {
    final ResponseParser responseParser = new ResponseParser(responseHandler, clientEnvironment.getCharset());
    final StringBuffer responseBuffer = new StringBuffer(32);
    for (; ;) {
      final String responseString = readResponse(connectionStreams.getLoggedReader(), responseBuffer);
      if (responseString.length() == 0) {
        return false;
      }

      final Boolean result = responseParser.processResponse(responseString, connectionStreams, responseServices, clientEnvironment);
      if (result != null) {
        return result.booleanValue();
      }

      checkCanceled();
    }
  }

  private static String readResponse(Reader reader, StringBuffer responseBuffer) throws IOException {
    responseBuffer.setLength(0);

    for (int chr = reader.read(); chr >= 0; chr = reader.read()) {
      if (chr == '\n' || chr == ' ') {
        break;
      }

      responseBuffer.append((char)chr);
    }

    return responseBuffer.toString();
  }

  private void sendFile(FileDetails fileDetails, IConnectionStreams connectionStreams) throws IOException {
    final FileObject fileObject = fileDetails.getFileObject();

    if (fileDetails.isBinary()) {
      clientEnvironment.getLocalFileReader().transmitBinaryFile(fileObject, connectionStreams, clientEnvironment.getCvsFileSystem());
    }
    else {
      clientEnvironment.getLocalFileReader().transmitTextFile(fileObject, connectionStreams, clientEnvironment.getCvsFileSystem());
    }
  }
}
