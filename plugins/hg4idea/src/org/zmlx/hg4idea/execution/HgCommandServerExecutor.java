/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.HgVcs;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


/**
 * This is the first implementation of command server execution.
 * There are no performance difference between style and templates, but when you execute mercurial commands via command server
 * you can use only one encoding; there is no way to change it, only start another server;
 * you can't execute several commands at the same time even they have no write action inside;
 * you can't stop command without stopping server;
 * you can't start more than N command server because of memory allocation problem:
 * -each command server require about several MB to start;
 * -if python process need more memory to execute the command it will never return it back to OS;
 * - if you perform hg log it may eat hundreds of MBs
 */
public class HgCommandServerExecutor {

  /**
   * State of the server.
   */
  public enum State {
    NOT_STARTED, STARTING, RUNNING, STOPPING, STOPPED, CRASHED
  }

  private static final Logger LOG = Logger.getInstance(HgCommandServerExecutor.class);
  private static final Object LOCK = new Object();
  private static final String RUNCOMMAND = "runcommand\n";

  private final String myHgExecutable;

  /**
   * The character enco ding for the server. It could not be changed after server starts.
   * http://mercurial.selenic.com/wiki/CommandServer#Encoding
   */
  private final Charset myCharset;
  private Process myProcess;
  private DataInputStream myStream;
  private DataOutputStream myOutputStream;

  public State getState() {
    return myStateReference.get();
  }

  private final AtomicReference<State> myStateReference = new AtomicReference<State>(State.NOT_STARTED);

  /**
   * The directory containing the Mercurial repository where the
   * server is running.
   */
  private File myDirectory;


  public HgCommandServerExecutor(Project project, Charset charset) {
    HgVcs vcs = HgVcs.getInstance(project);
    myHgExecutable = vcs.getGlobalSettings().getHgExecutable();
    myCharset = charset;
  }


  //Server could be started only with hg repository directory, but used for any.
  public void startServer(File directory) {
    synchronized (LOCK) {
      this.myDirectory = directory;
      if (getState() != State.NOT_STARTED) {
        return;
      }
      List<String> args = Lists
        .newArrayList(myHgExecutable, "serve", "--cmdserver", "pipe");
      GeneralCommandLine commandLine = new GeneralCommandLine(args);
      if (directory != null) {
        commandLine.setWorkDirectory(directory);
      }
      if (myCharset != null) {
        commandLine.setCharset(myCharset);
      }

      myStateReference.set(State.STARTING);
      try {

        myProcess = commandLine.createProcess();
        myStream = new DataInputStream(myProcess.getInputStream());
        myOutputStream = new DataOutputStream(myProcess.getOutputStream());
        //noinspection ResultOfMethodCallIgnored
        myStream.read();
        int byteNum = myStream.readInt();
        long skipped = myStream.skip(byteNum);
        if (skipped != byteNum) throw new IllegalStateException();
        myOutputStream.flush();
      }
      catch (ExecutionException e) {
        LOG.error("Could not start hg command server!", e);
        myStateReference.set(State.CRASHED);
        return;
      }
      catch (IOException e) {
        LOG.error("Could not start hg command server!", e);
        myStateReference.set(State.CRASHED);
        return;
      }
      myStateReference.set(State.RUNNING);
      LOG.info("Command server started: " + this.myDirectory);
    }
  }

  /**
   * Stop the Mercurial server process
   */
  public void stop() {
    synchronized (LOCK) {
      if (myProcess == null || this.myStateReference.get() == State.STOPPED) {
        LOG.warn("Trying to stop already stopped server");
        return;
      }

      myStateReference.set(State.STOPPING);
      try {
        myOutputStream.flush();
        myOutputStream.close();
        myStream.close();
      }
      catch (IOException e) {
        myStateReference.set(State.CRASHED);
        return;
      }
      try {
        myProcess.waitFor();
      }
      catch (InterruptedException e) {
        LOG.error("Process for Mercurial server interrupted", e);
        myStateReference.set(State.CRASHED);
        return;
      }
      finally {
        myProcess = null;
        myStream = null;
        myOutputStream = null;
      }
      myStateReference.set(State.STOPPED);
    }
  }

  public HgCommandResult runCommand(List<String> cmdLine) throws IOException, InterruptedException {
    synchronized (LOCK) {
      try {
        if (myStateReference.get() == State.RUNNING) {
          return sendCommand(cmdLine);
        }
      }
      catch (InterruptedException e) {
        stop();
        myStateReference.set(State.NOT_STARTED);
        startServer(myDirectory);
      }
      return HgCommandResult.EMPTY;
    }
  }


  private HgCommandResult sendCommand(List<String> cmdLine) throws IOException, InterruptedException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    outputStream.write(cmdLine.get(0).getBytes(myCharset));
    for (String s : cmdLine.subList(1, cmdLine.size())) {
      outputStream.write('\0');
      outputStream.write(s.getBytes(myCharset));
    }
    return execute(outputStream.toByteArray());
  }

  private HgCommandResult execute(byte[] command) throws InterruptedException {
    try {
      myOutputStream.write(RUNCOMMAND.getBytes(myCharset));
      myOutputStream.writeInt(command.length);
      myOutputStream.write(command);
      myOutputStream.flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return readBlock();
  }

  private HgCommandResult readBlock() throws InterruptedException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    try {
      while (true) {
        if (indicator != null && indicator.isCanceled()) {
          throw new InterruptedException("hg command cancelled");
        }
        char channel = (char)myStream.read();
        int byteNum = myStream.readInt();
        byte[] bios = new byte[byteNum];
        switch (channel) {
          case 'o':
            if (byteNum != myStream.read(bios)) {
              throw new IllegalStateException();
            }
            out.write(new String(bios));
            break;
          case 'e':
            if (byteNum != myStream.read(bios)) throw new IllegalStateException();
            err.write(new String(bios));
            break;
          case 'r':
            if (byteNum != 4) {
              throw new RuntimeException("incorrect byte number in result channel");
            }
            //if (byteNum != input.read(bios)) throw new IllegalStateException();
            return new HgCommandResult(out, err, myStream.readInt());
          case 'L':
            return null;
          default:
            return null;
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Unexpected exception during hg output reading.", e);
    }
    return null;
  }
}
