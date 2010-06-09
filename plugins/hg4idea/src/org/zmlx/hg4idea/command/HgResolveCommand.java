// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgUtil;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public class HgResolveCommand {

  private static File FILEMERGE_PLUGIN;

  private static final int ITEM_COUNT = 3;

  private final Project project;

  public HgResolveCommand(Project project) {
    this.project = project;
    if (FILEMERGE_PLUGIN == null) {
      FILEMERGE_PLUGIN = HgUtil.getTemporaryPythonFile("filemerge");
    }
  }

  public Map<HgFile, HgResolveStatusEnum> list(VirtualFile repo) {
    if (repo == null) {
      return Collections.emptyMap();
    }

    HgCommandResult result = HgCommandService.getInstance(project)
      .execute(repo, "resolve", Arrays.asList("--list"));

    Map<HgFile, HgResolveStatusEnum> resolveStatus = new HashMap<HgFile, HgResolveStatusEnum>();
    for (String line : result.getOutputLines()) {
      if (StringUtils.isBlank(line) || line.length() < ITEM_COUNT) {
        continue;
      }
      HgResolveStatusEnum status = HgResolveStatusEnum.valueOf(line.charAt(0));
      if (status != null) {
        File ioFile = new File(repo.getPath(), line.substring(2));
        resolveStatus.put(new HgFile(repo, ioFile), status);
      }
    }
    return resolveStatus;
  }

  public void markResolved(VirtualFile repo, VirtualFile path) {
    HgCommandService.getInstance(project)
      .execute(repo, "resolve", Arrays.asList("--mark", path.getPath()));
  }

  public void markResolved(VirtualFile repo, FilePath path) {
    HgCommandService.getInstance(project)
      .execute(repo, "resolve", Arrays.asList("--mark", path.getPath()));
  }

  public MergeData getResolveData(VirtualFile repo, VirtualFile path) throws VcsException {
    //start an hg resolve command, configured with a mercurial extension
    //which will transfer the contents of the base, local and other versions
    //to the socket server we set up on this end.

    if (FILEMERGE_PLUGIN == null) {
      throw new VcsException("Could not provide dynamic extension file");
    }
    Receiver receiver = new Receiver();
    SocketServer server = new SocketServer(receiver);
    try {
      int port = server.start();

      List<String> hgOptions = Arrays.asList(
        "--config", "extensions.hg4ideafilemerge=" + FILEMERGE_PLUGIN.getAbsolutePath(),
        "--config", "hg4ideafilemerge.port=" + port);
      ensureSuccess(HgCommandService.getInstance(project).
        execute(repo, hgOptions, "resolve", Arrays.asList(path.getPath())));

    } catch (IOException e) {
      throw new VcsException(e);
    }
    try {
      return receiver.getMergeParticipantsContents();
    } catch (InterruptedException e) {
      //operation was cancelled, never mind what the contents of all the participants is.
      return null;
    }
  }

  public static class Receiver extends SocketServer.Protocol{

    private MergeData data;
    private final CountDownLatch completed = new CountDownLatch(1);

    public boolean handleConnection(Socket socket) throws IOException {
      DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      byte[] local = readDataBlock(dataInputStream);
      byte[] other = readDataBlock(dataInputStream);
      byte[] base = readDataBlock(dataInputStream);

      new PrintStream(socket.getOutputStream()).println("Done");

      data = new MergeData(local, other, base);
      completed.countDown();
      return false;
    }

    private MergeData getMergeParticipantsContents() throws InterruptedException, VcsException {
      //join defines a 'happens-before' relationship, so no need for extra synchronization
      completed.await(1, TimeUnit.SECONDS);
      if (data == null) {
        throw new VcsException("Did not receive data from Mercurial's resolve command");
      }
      return data;
    }

  }

  public final static class MergeData {
    private final byte[] local;
    private final byte[] other;
    private final byte[] base;

    private MergeData(byte[] local, byte[] other, byte[] base) {
      this.local = local;
      this.other = other;
      this.base = base;
    }

    public byte[] getLocal() {
      return local;
    }

    public byte[] getOther() {
      return other;
    }

    public byte[] getBase() {
      return base;
    }
  }
}