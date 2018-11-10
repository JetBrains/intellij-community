/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.reservedcheckout.EditCommand;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class EditOperation extends CvsOperationOnFiles {
  private final boolean myIsReservedEdit;
  private final List<EditedFileInfo> myEditFileInfos = new ArrayList();
  @NonNls public static final String FILES_BEING_EDITED_EXCEPTION = "cvs [edit aborted]: files being edited";

  private final static class EditedFileInfo {
    private final String myFileName;
    private final String myUser;
    private final String myHost;
    private final File myEditLocation;

    EditedFileInfo(String fileName, String user, String host, String editLocation) {
      myFileName = fileName;
      myUser = user;
      myHost = host;
      myEditLocation = new File(editLocation);
    }

    public static EditedFileInfo createOn(String editInfoString){
      final StringTokenizer tokens = new StringTokenizer(editInfoString, "\t");
      if (!tokens.hasMoreTokens()) return null;
      final String fileName = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      final String user = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      final String host = tokens.nextToken();
      if (!tokens.hasMoreTokens()) return null;
      final String editLocation = tokens.nextToken();
      return new EditedFileInfo(fileName, user, host, editLocation);
    }

    public File getFile(CvsRootProvider root){
      return new File(root.getLocalRoot(), myFileName);
    }

    public boolean isSuitableFor(CvsRootProvider root) {
      try {
        final String hostName = InetAddress.getLocalHost().getHostName();
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

  @Override
  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final EditCommand result = new EditCommand();
    result.setTemporaryWatch(Watch.TALL);
    result.setCheckThatUnedited(myIsReservedEdit);
    result.setForceEvenIfEdited(false);
    addFilesToCommand(root, result);
    return result;
  }

  @Override
  protected void execute(CvsRootProvider root,
                         CvsExecutionEnvironment executionEnvironment,
                         ReadWriteStatistics statistics, IProgressViewer progressViewer) throws CommandException, VcsException {
    super.execute(root, executionEnvironment, statistics, progressViewer);
    final VcsException vcsException = new CvsException(FILES_BEING_EDITED_EXCEPTION, root.getCvsRootAsString());

    for (EditedFileInfo info : myEditFileInfos) {
      if (info.isSuitableFor(root)) return;

      final File file = info.getFile(root);
      final VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(file);
      if (virtualFile != null) vcsException.setVirtualFile(virtualFile);
    }

    if (!myEditFileInfos.isEmpty()) throw vcsException;
  }

  @Override
  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    final EditedFileInfo editedFileInfo = EditedFileInfo.createOn(message);
    if (editedFileInfo != null)
      myEditFileInfos.add(editedFileInfo);
  }

  @Override
  protected String getOperationName() {
    return "edit";
  }
}
