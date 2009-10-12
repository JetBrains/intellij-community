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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A default implementation of the {@link UpdateSession} interface. This implementation can
 * be used if no post-update processing is required. 
 */
public class UpdateSessionAdapter implements UpdateSession{
  private final List<VcsException> myExceptions;
  private final boolean myIsCanceled;

  public UpdateSessionAdapter(List<VcsException> exceptions, boolean isCanceled) {
    myExceptions = exceptions;
    myIsCanceled = isCanceled;
  }

  @NotNull
  public List<VcsException> getExceptions() {
    return myExceptions;
  }

  public void onRefreshFilesCompleted() {
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }
}
