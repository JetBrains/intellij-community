/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class AnnotationsPreloader {
  private final Alarm myAlarm;

  public AnnotationsPreloader(final Project project) {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull final VirtualFile file) {
        if (!Registry.is("vcs.annotations.preload")) return;

        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
        if (vcs == null) return;
        final AnnotationProvider annotationProvider = vcs.getCachingAnnotationProvider();
        assert annotationProvider != null;

        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            try {
              annotationProvider.annotate(file);
            }
            catch (VcsException ignore) {
            }
          }
        }, 0);
      }
    });
  }
}
