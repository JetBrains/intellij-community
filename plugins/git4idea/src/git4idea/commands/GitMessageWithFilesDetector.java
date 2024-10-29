// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The listener of {@link GitLineHandler} which watches Git output and detects some message in console,
 * captures the following list of files and stops saving them when another message occurs.
 * <p>
 * For example, the situation, when local changes would be overwritten by checkout.
 *
 * @see GitSimpleEventDetector
 */
public class GitMessageWithFilesDetector implements GitLineEventDetector {
  private static final Logger LOG = Logger.getInstance(GitMessageWithFilesDetector.class);

  private final @NotNull Event myEvent;
  private final @NotNull VirtualFile myRoot;

  protected final @NotNull Set<String> myAffectedFiles = new HashSet<>();
  protected boolean myMessageDetected;
  private @Nullable Key myMessageOutputType;

  public GitMessageWithFilesDetector(@NotNull Event event, @NotNull VirtualFile root) {
    myEvent = event;
    myRoot = root;
  }

  @Override
  public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
    if (ContainerUtil.exists(myEvent.messageStartMarkers, marker -> marker.matcher(line).matches())) {
      LOG.debug("|" + myEvent.name + "| message start marker detected in [" + line + "]" + "of type " + outputType);
      myMessageDetected = true;
      myMessageOutputType = outputType;
      return;
    }
    if (ContainerUtil.exists(myEvent.messageEndMarkers, marker -> marker.matcher(line).matches())) {
      LOG.debug("|" + myEvent.name + "| message end marker detected in [" + line + "]" + "of type " + outputType);
      myMessageOutputType = null;
      return;
    }
    if (outputType.equals(myMessageOutputType)) {
      LOG.debug("|" + myEvent.name + "| Treating as a file: [" + line + "]" + "of type " + outputType);
      myAffectedFiles.add(line.trim());
      return;
    }
    else {
      LOG.debug("|" + myEvent.name + "| Plain message: [" + line + "]" + "of type " + outputType);
    }
  }

  /**
   * @return if the error "Your local changes to the following files would be overwritten by checkout" has happened.
   *
   * @deprecated replaced with {@link #isDetected()}
   */
  @Deprecated
  public boolean wasMessageDetected() {
    return isDetected();
  }

  @Override
  public boolean isDetected() {
    return myMessageDetected;
  }

  /**
   * @return the set of files (maybe empty) that would be overwritten by checkout, as told by Git.
   */
  public @NotNull Set<String> getRelativeFilePaths() {
    return myAffectedFiles;
  }

  public @NotNull Collection<VirtualFile> getFiles() {
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
    private final @NotNull @NonNls String name;
    private final @NotNull List<Pattern> messageStartMarkers;
    private final @NotNull List<Pattern> messageEndMarkers;

    Event(@NotNull @NonNls String eventName,
          @NotNull List<Pattern> messageStartMarkers,
          @NotNull List<Pattern> messageEndMarkers) {
      name = eventName;
      this.messageStartMarkers = messageStartMarkers;
      this.messageEndMarkers = messageEndMarkers;
    }
  }
}
