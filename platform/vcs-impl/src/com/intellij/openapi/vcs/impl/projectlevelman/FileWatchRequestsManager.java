/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RequestsMerger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.Consumer;

/**
* @author irengrig
*/
public class FileWatchRequestsManager {
  private final RequestsMerger myMerger;
  private final Project myProject;

  public FileWatchRequestsManager(final Project project, final NewMappings newMappings, final LocalFileSystem localFileSystem) {
    myProject = project;
    myMerger = new RequestsMerger(new FileWatchRequestModifier(project, newMappings, localFileSystem), new Consumer<Runnable>() {
      @Override
      public void consume(Runnable runnable) {
        if ((! myProject.isInitialized()) || myProject.isDisposed()) return;
        final Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode()) {
          runnable.run();
        } else {
          application.executeOnPooledThread(runnable);
        }
      }
    });
  }

  public void ping() {
    myMerger.request();
  }
}
