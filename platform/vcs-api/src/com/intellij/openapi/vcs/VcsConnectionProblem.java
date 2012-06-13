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
package com.intellij.openapi.vcs;

import java.util.Collection;

/**
 * @author peter
 */
public class VcsConnectionProblem extends VcsException {
  public VcsConnectionProblem(String message) {
    super(message);
  }

  public VcsConnectionProblem(Throwable throwable, boolean isWarning) {
    super(throwable, isWarning);
  }

  public VcsConnectionProblem(Throwable throwable) {
    super(throwable);
  }

  public VcsConnectionProblem(String message, Throwable cause) {
    super(message, cause);
  }

  public VcsConnectionProblem(String message, boolean isWarning) {
    super(message, isWarning);
  }

  public VcsConnectionProblem(Collection<String> messages) {
    super(messages);
  }
}
