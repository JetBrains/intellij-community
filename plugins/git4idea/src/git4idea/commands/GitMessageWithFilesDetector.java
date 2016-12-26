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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The listener of {@link GitLineHandler} which watches Git output and detects some message in console,
 * captures the following list of files and stops saving them when another message occurs.
 *
 * For example, the situation, when local changes would be overwritten by checkout.
 *
 * @see GitSimpleEventDetector
 */
public class GitMessageWithFilesDetector implements GitLineHandlerListener {
  private static final Logger LOG = Logger.getInstance(GitMessageWithFilesDetector.class);

  @NotNull private final Event myEvent;
  @NotNull private final VirtualFile myRoot;

  @NotNull protected final Set<String> myAffectedFiles = new HashSet<>();
  protected boolean myMessageDetected;
  @Nullable private Key myMessageOutputType;

  public GitMessageWithFilesDetector(@NotNull Event event, @NotNull VirtualFile root) {
    myEvent = event;
    myRoot = root;
  }

  @Override
  public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
    if (line.contains(myEvent.messageStartMarker)) {
      LOG.debug("|" + myEvent.name + "| message start marker detected in [" + line + "]" + "of type " + outputType);
      myMessageDetected = true;
      myMessageOutputType = outputType;
    }
    else if (line.contains(myEvent.messageEndMarker)) {
      LOG.debug("|" + myEvent.name + "| message end marker detected in [" + line + "]" + "of type " + outputType);
      myMessageOutputType = null;
    }
    else if (outputType.equals(myMessageOutputType)) {
      LOG.debug("|" + myEvent.name + "| Treating as a file: [" + line + "]" + "of type " + outputType);
      myAffectedFiles.add(line.trim());
    }
    else {
      LOG.debug("|" + myEvent.name + "| Plain message: [" + line + "]" + "of type " + outputType);
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
    @NotNull private final String name;
    @NotNull private final String messageStartMarker;
    @NotNull private final String messageEndMarker;

    Event(@NotNull String eventName, @NotNull String messageStartMarker, @NotNull String messageEndMarker) {
      name = eventName;
      this.messageStartMarker = messageStartMarker;
      this.messageEndMarker = messageEndMarker;
    }
  }
}
