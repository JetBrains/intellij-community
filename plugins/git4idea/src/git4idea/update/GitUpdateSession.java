/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.update;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdateSession;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Git update session implementation
 */
public class GitUpdateSession implements UpdateSession {
  private final boolean myResult;

  public GitUpdateSession(boolean result) {
    myResult = result;
  }

  @NotNull
  public List<VcsException> getExceptions() {
    return Collections.emptyList();
  }

  public void onRefreshFilesCompleted() {
  }

  public boolean isCanceled() {
    return !myResult;
  }
}
