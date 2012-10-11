/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Detects the error which happens, when some untracked working tree files prevent merge or checkout.</p>
 *
 * <p>Using a separate detector class instead of defining just an {@link GitMessageWithFilesDetector.Event},
 * because older versions of Git have other format of displaying this error that doesn't match any existing detectors.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitUntrackedFilesOverwrittenByOperationDetector extends GitMessageWithFilesDetector {

  private static final Pattern OLD_UNTRACKED_FILES_PATTERN = Pattern.compile(
    ".*Untracked working tree file '(.*)' would be overwritten by.*"
  );

  private static final Event NEW_UNTRACKED_FILES_OVERWRITTEN_BY = new Event(
    "The following untracked working tree files would be overwritten by",
    "Please move or remove them before"
  );

  public GitUntrackedFilesOverwrittenByOperationDetector(VirtualFile root) {
    super(NEW_UNTRACKED_FILES_OVERWRITTEN_BY, root);
  }

  @Override
  public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
    super.onLineAvailable(line, outputType);
    Matcher m = OLD_UNTRACKED_FILES_PATTERN.matcher(line);
    if (m.matches()) {
      myMessageDetected = true;
      myAffectedFiles.add(m.group(1));
    }
  }
}
