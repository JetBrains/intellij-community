package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

import com.intellij.openapi.util.text.StringUtil;

public class RtagCommand extends AbstractCommand{
  private final String myTagName;
  private boolean myOverrideExistings = false;

  public RtagCommand(String tagName) {
    myTagName = tagName;
  }

  public final boolean execute(IRequestProcessor requestProcessor,
                               IEventSender eventSender,
                               ICvsListenerRegistry listenerRegistry,
                               IClientEnvironment clientEnvironment,
                               IProgressViewer progressViewer) throws CommandException {
    final Requests requests = new Requests(CommandRequest.RTAG, clientEnvironment);
    requests.addArgumentRequest(myOverrideExistings, "-F");
    requests.addArgumentRequest(true, myTagName);
    for (Iterator iterator = getFileObjects().getFileObjects().iterator(); iterator.hasNext();) {
      FileObject fileObject = (FileObject)iterator.next();
      String path = fileObject.getPath();
      if (StringUtil.startsWithChar(path, '/')) path = path.substring(1);
      requests.addArgumentRequest(path);
    }
    return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
  }


  public final String getCvsCommandLine() {
    @NonNls final StringBuffer cvsCommandLine = new StringBuffer("rtag ");
    cvsCommandLine.append(getCVSArguments());
    appendFileArguments(cvsCommandLine);
    return cvsCommandLine.toString();
  }

  private String getCVSArguments() {
    @NonNls final StringBuffer cvsArguments = new StringBuffer();
    cvsArguments.append("-F " + myTagName);
    return cvsArguments.toString();
  }

  public void setOverrideExistings(boolean overrideExistings) {
    myOverrideExistings = overrideExistings;
  }
}