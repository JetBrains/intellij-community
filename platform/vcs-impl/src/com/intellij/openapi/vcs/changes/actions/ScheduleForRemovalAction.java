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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:08:21
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

import java.util.Collections;
import java.util.List;

public class ScheduleForRemovalAction extends AbstractMissingFilesAction {
  protected List<VcsException> processFiles(final AbstractVcs vcs, final List<FilePath> files) {
    CheckinEnvironment environment = vcs.getCheckinEnvironment();
    if (environment == null) return Collections.emptyList();
    final List<VcsException> result = environment.scheduleMissingFileForDeletion(files);
    if (result == null) return Collections.emptyList();
    return result;
  }

  protected String getName() {
    return null;
  }

  protected boolean synchronously() {
    return true;
  }
}