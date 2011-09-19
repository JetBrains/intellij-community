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
package git4idea.process;

import com.intellij.openapi.util.Key;
import git4idea.commands.GitLineHandlerListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * The listener of {@link git4idea.commands.GitLineHandler} which detects the situation, when local changes would be overwritten by checkout.
 * Detects the error and captures the list of files which would be overwritten.
 * 
 * @author Kirill Likhodedov
 */
class GitWouldBeOverwrittenByCheckoutDetector implements GitLineHandlerListener {

  private final Set<String> myAffectedFiles = new HashSet<String>();
  private boolean myWouldBeOverwrittenError;
  private boolean myFilesAreDisplayed;

  @Override
  public void onLineAvailable(String line, Key outputType) {
    if (line.contains("Your local changes to the following files would be overwritten by checkout")) {
      myWouldBeOverwrittenError = true;
      myFilesAreDisplayed = true;
    } else if (line.contains("commit your changes or stash them before")) {
      myFilesAreDisplayed = false;
    } else if (myFilesAreDisplayed) {
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
  public boolean isWouldBeOverwrittenError() {
    return myWouldBeOverwrittenError;
  }

  /**
   * @return the set of files (maybe empty) that would be overwritten by checkout, as told by Git.
   */
  @NotNull
  public Set<String> getAffectedFiles() {
    return myAffectedFiles;
  }

}
