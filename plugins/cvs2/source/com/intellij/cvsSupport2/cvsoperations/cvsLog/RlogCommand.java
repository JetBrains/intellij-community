package com.intellij.cvsSupport2.cvsoperations.cvsLog;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

/**
 * author: lesya
 */
public class RlogCommand extends AbstractCommand {

  public RlogCommand() {
  }

  // Implemented ============================================================

  public final boolean execute(IRequestProcessor requestProcessor,
                               IEventSender eventSender,
                               ICvsListenerRegistry listenerRegistry,
                               IClientEnvironment clientEnvironment,
                               IProgressViewer progressViewer) throws CommandException {
    final Requests requests = new Requests(CommandRequest.RLOG, clientEnvironment);
    requests.addArgumentRequest(true, "-h");
    requests.addArgumentRequest(".");
    return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
  }


  public final String getCvsCommandLine() {
    final StringBuffer cvsCommandLine = new StringBuffer("log ");
    cvsCommandLine.append(getCVSArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  public final void resetCvsCommand() {
    super.resetCvsCommand();
    setRecursive(true);
  }

  private String getCVSArguments() {
    final StringBuffer cvsArguments = new StringBuffer();
    cvsArguments.append("-h ");
    return cvsArguments.toString();
  }

  // Utils ==================================================================

  protected final void addModifiedRequest(FileObject fileObject,
                                          Entry entry,
                                          Requests requests,
                                          IClientEnvironment clientEnvironment) {
    requests.addIsModifiedRequest(fileObject);
  }
}
