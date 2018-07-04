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

import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
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

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;

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
  private final long myTimeout;

  // Setup ==================================================================
  public RequestProcessor(IClientEnvironment clientEnvironment,
                          IGlobalOptions globalOptions,
                          IEventSender eventSender,
                          IStreamLogger streamLogger,
                          ICvsCommandStopper commandStopper) {
    this(clientEnvironment, globalOptions, eventSender, streamLogger, commandStopper, -1);
  }

  public RequestProcessor(IClientEnvironment clientEnvironment,
                          IGlobalOptions globalOptions,
                          IEventSender eventSender,
                          IStreamLogger streamLogger,
                          ICvsCommandStopper commandStopper, final long timeout) {
    myTimeout = timeout;
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

  public boolean processRequests(Requests requests, IRequestsProgressHandler communicationProgressHandler) throws CommandException,
                                                                                                                  AuthenticationException {
    IConnectionStreams connectionStreams = openConnection();
    try {
      return processRequests(requests, connectionStreams, communicationProgressHandler);
    }
    finally {
      connectionStreams.close();
    }
  }

  // Utils ==================================================================

  private IConnectionStreams openConnection() throws CommandException, AuthenticationException {
    clientEnvironment.getConnection().open(streamLogger);

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

  private boolean processRequests(final Requests requests,
                                  final IConnectionStreams connectionStreams,
                                  final IRequestsProgressHandler communicationProgressHandler)
    throws CommandException, IOCommandException {

    BugLog.getInstance().assertNotNull(requests);

    /*final ProcessRequestsHelper helper = (myTimeout == -1) ?
                                         new DirectProcessRequestHelper() : new TimedOutProcessRequestHelper();*/
    final ProcessRequestsHelper helper = new DirectProcessRequestHelper();
    return helper.processRequests(requests, connectionStreams, communicationProgressHandler);
  }

  private abstract class ProcessRequestsHelper {
    protected IOException myIOException;
    protected CommandException myCommandException;
    protected boolean myResult;

    protected abstract void before();
    protected abstract void callRunnable(final Runnable runnable);
    protected abstract void afterInRunnable();
    protected abstract void after() throws CommandException;

    public boolean processRequests(final Requests requests,
                                   final IConnectionStreams connectionStreams,
                                   final IRequestsProgressHandler communicationProgressHandler)
      throws CommandException, IOCommandException {
      final Runnable runnable = () -> {
        try {
          checkCanceled();
          sendRequests(requests, connectionStreams, communicationProgressHandler);
          checkCanceled();

          sendRequest(requests.getResponseExpectingRequest(), connectionStreams);
          connectionStreams.flushForReading();

          myResult = handleResponses(connectionStreams, new DefaultResponseHandler());
        }
        catch (IOException e) {
          myIOException = e;
        }
        catch (CommandException e) {
          myCommandException = e;
        }
        finally {
          afterInRunnable();
        }
      };

      before();
      callRunnable(runnable);
      if (myIOException != null) throw new IOCommandException(myIOException);
      if (myCommandException != null) throw myCommandException;
      after();

      return myResult;
    }
  }

  private class TimedOutProcessRequestHelper extends ProcessRequestsHelper {
    private final Semaphore mySemaphore;
    private Future<?> myFuture;

    private TimedOutProcessRequestHelper() {
      mySemaphore = new Semaphore();
    }

    @Override
    protected void before() {
      mySemaphore.down();
    }

    @Override
    protected void callRunnable(Runnable runnable) {
      myFuture = ConcurrencyUtil.newSingleThreadExecutor("CVS request").submit(runnable);

      final long tOut = (myTimeout < 20000) ? 20000 : myTimeout;
      while (true) {
        mySemaphore.waitFor(tOut);
        if (myFuture.isDone() || myFuture.isCancelled()) break;
        if (! commandStopper.isAlive()) break;
        commandStopper.resetAlive();
      }
    }

    @Override
    protected void afterInRunnable() {
      mySemaphore.up();
    }

    @Override
    protected void after() throws CommandException {
      if ((! myFuture.isDone() && (! myFuture.isCancelled()) && (! commandStopper.isAlive()))) {
        myFuture.cancel(true);
        throw new CommandException(new CommandAbortedException(), "Command execution timed out");
      }
    }
  }

  private class DirectProcessRequestHelper extends ProcessRequestsHelper {
    @Override
    protected void before() {
    }

    @Override
    protected void callRunnable(Runnable runnable) {
      runnable.run();
    }

    @Override
    protected void afterInRunnable() {
    }

    @Override
    protected void after() {
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
    throws CommandException, IOException {
    //final ErrorDefendingResponseHandler proxy = new ErrorDefendingResponseHandler(myTimeout, responseHandler);

    final ResponseParser responseParser = new ResponseParser(responseHandler, clientEnvironment.getCharset());
    final StringBuilder responseBuffer = new StringBuilder(32);
    while (true) {
      readResponse(connectionStreams.getLoggedReader(), responseBuffer);
      checkCanceled();
      if (responseBuffer.length() == 0) {
        return false;
      }

      final Boolean result =
        responseParser.processResponse(responseBuffer.toString(), connectionStreams, responseServices, clientEnvironment);
      if (result != null) {
        return result.booleanValue();
      }

      checkCanceled();
      /*if (proxy.interrupt()) {
        throw new CommandException(null, "Aborted: consequent errors stream");
      }*/
    }
  }

  private static void readResponse(Reader reader, StringBuilder responseBuffer) throws IOException {
    responseBuffer.setLength(0);

    for (int chr = reader.read(); chr >= 0; chr = reader.read()) {
      if (chr == '\n' || chr == ' ') {
        break;
      }
      responseBuffer.append((char)chr);
    }
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
