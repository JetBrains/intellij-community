package com.intellij.cvsSupport2.cvsoperations.cvsAnnotate;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperationHelper;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.annotate.AnnotateCommand;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class AnnotateOperation extends LocalPathIndifferentOperation {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation");

  private final String myRevision;
  private final List<Annotation> myAnnotations = new ArrayList<Annotation>();
  private final StringBuffer myBuffer = new StringBuffer();
  private final LocalPathIndifferentOperationHelper myHelper;

  public static AnnotateOperation createForFile(File file){
    File cvsLightweightFile = CvsUtil.getCvsLightweightFileForFile(file);
    String revision = CvsUtil.getRevisionFor(file);
    return new AnnotateOperation(cvsLightweightFile,
        revision, CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParentFile()));
  }

  public AnnotateOperation(File cvsLightweightFile, String revision, CvsEnvironment env) {
    this(cvsLightweightFile, revision, env, new LocalPathIndifferentOperationHelper(revision));
  }
  
  private AnnotateOperation(File cvsLightweightFile, String revision, CvsEnvironment env, 
                            LocalPathIndifferentOperationHelper helper){
    super(helper.getAdminReader(), env);
    myHelper = helper;
    myHelper.addFile(cvsLightweightFile);
    myRevision = revision;

  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    AnnotateCommand result = new AnnotateCommand();
    myHelper.addFilesTo(result);
    result.setAnnotateByRevisionOrTag(myRevision);
    return result;
  }

  public Annotation[] getLineAnnotations(){
    return myAnnotations.toArray(new Annotation[myAnnotations.size()]);
  }

  public String getContent(){
    return myBuffer.toString();
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    if (!error) {
      try {
        myAnnotations.add(Annotation.createOnMessage(message));
        myBuffer.append(Annotation.createMessageOn(message));
        myBuffer.append('\n');
      } catch (ParseException e) {
        LOG.error(e);
      }
    }
  }

  protected String getOperationName() {
    return "annotate";
  }
}
