package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.reservedcheckout.EditCommand;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class EditOperation extends CvsOperationOnFiles {
  private final boolean myIsReservedEdit;
  private final Collection myEditFileInfos = new ArrayList();
  @NonNls public static final String FILES_BEING_EDITED_EXCEPTION = "cvs [edit aborted]: files being edited";

  private final static class EditedFileInfo {
    private final String myFileName;
    private final String myUser;
    private final String myHost;
    private final File myEditLocation;

    public EditedFileInfo(String fileName, String user, String host, String editLocation) {
      myFileName = fileName;
      myUser = user;
      myHost = host;
      myEditLocation = new File(editLocation);
    }

    public static EditedFileInfo createOn(String editInfoString){
      StringTokenizer tokens = new StringTokenizer(editInfoString, "\t");
      if (!tokens.hasMoreTokens()) return null;
      String fileName = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      String user = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      String host = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      String editLocation = tokens.nextToken();
      return new EditedFileInfo(fileName, user, host, editLocation);
    }

    public File getFile(CvsRootProvider root){
      return new File(root.getLocalRoot(), myFileName);
    }

    public boolean isSuitableFor(CvsRootProvider root) {
      try {
        String hostName = InetAddress.getLocalHost().getHostName();
        return myUser.equals(root.getCvsRoot().getUser())
            && myEditLocation.equals(getFile(root).getParentFile())
            && myHost.equals(hostName);
      } catch (UnknownHostException e) {
        LOG.error(e);
        return false;
      }
    }
  }

  public EditOperation(boolean isReservedEdit) {
    myIsReservedEdit = isReservedEdit;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    EditCommand result = new EditCommand();
    result.setTemporaryWatch(Watch.TALL);
    result.setCheckThatUnedited(myIsReservedEdit);
    result.setForceEvenIfEdited(false);
    addFilesToCommand(root, result);
    return result;
  }

  protected void execute(CvsRootProvider root,
                         CvsExecutionEnvironment executionEnvironment,
                         ReadWriteStatistics statistics, ModalityContext executor) throws CommandException,
      CommandAbortedException, VcsException {
    super.execute(root, executionEnvironment, statistics, executor);
    final VcsException vcsException = new CvsException(FILES_BEING_EDITED_EXCEPTION, root.getCvsRootAsString());

    for (Iterator iterator = myEditFileInfos.iterator(); iterator.hasNext();) {
      EditedFileInfo info = (EditedFileInfo) iterator.next();

      if (info.isSuitableFor(root)) return;

      final File file = info.getFile(root);
      VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(file);
      if (virtualFile != null) vcsException.setVirtualFile(virtualFile);

    }

    if (!myEditFileInfos.isEmpty()) throw vcsException;

  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    EditedFileInfo editedFileInfo = EditedFileInfo.createOn(message);
    if (editedFileInfo != null)
      myEditFileInfos.add(editedFileInfo);
  }

  protected String getOperationName() {
    return "edit";
  }
}
