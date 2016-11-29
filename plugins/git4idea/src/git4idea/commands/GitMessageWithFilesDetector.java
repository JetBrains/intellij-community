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
package git4idea.commands;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The listener of {@link git4idea.commands.GitLineHandler} which watches Git output and detects some message in console,
 * captures the following list of files and stops saving them when another message occurs.
 *
 * For example, the situation, when local changes would be overwritten by checkout.
 *
 * @see GitSimpleEventDetector
 * @author Kirill Likhodedov
 */
public class GitMessageWithFilesDetector implements GitLineHandlerListener {

  private final Event myEvent;
  private final VirtualFile myRoot;

  protected final Set<String> myAffectedFiles = new HashSet<>();
  protected boolean myMessageDetected;
  private boolean myFilesAreDisplayed;

  public GitMessageWithFilesDetector(@NotNull Event event, @NotNull VirtualFile root) {
    myEvent = event;
    myRoot = root;
  }

  @Override
  public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
    if (line.contains(myEvent.getMessageStartMarker())) {
      myMessageDetected = true;
      myFilesAreDisplayed = true;
    }
    else if (line.contains(myEvent.getMessageEndMarker())) {
      myFilesAreDisplayed = false;
    }
    else if (myFilesAreDisplayed) {
      myAffectedFiles.add(line.trim());
    }
  }

  @Override
  public void processTerminated(int exitCode) {
  }

  @Override
  public void startFailed(Throwable exception) {
  }

  /**
   * @return if the error "Your local changes to the following files would be overwritten by checkout" has happened.
   */
  public boolean wasMessageDetected() {
    return myMessageDetected;
  }

  /**
   * @return the set of files (maybe empty) that would be overwritten by checkout, as told by Git.
   */
  @NotNull
  public Set<String> getRelativeFilePaths() {
    return myAffectedFiles;
  }
  
  @NotNull
  public Collection<VirtualFile> getFiles() {
    Collection<VirtualFile> files = new ArrayList<>(myAffectedFiles.size());
    for (String affectedFile : myAffectedFiles) {
      VirtualFile file = myRoot.findFileByRelativePath(FileUtil.toSystemIndependentName(affectedFile));
      if (file != null) {
        files.add(file);
      }
    }
    return files;
  }

  public static class Event {
    private final String myMessageStartMarker;
    private final String myMessageEndMarker;

    Event(String messageStartMarker, String messageEndMarker) {
      myMessageStartMarker = messageStartMarker;
      myMessageEndMarker = messageEndMarker;
    }

    public String getMessageStartMarker() {
      return myMessageStartMarker;
    }

    public String getMessageEndMarker() {
      return myMessageEndMarker;
    }
  }

}
