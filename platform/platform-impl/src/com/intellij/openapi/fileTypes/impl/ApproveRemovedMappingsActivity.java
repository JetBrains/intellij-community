/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 3/11/13
 */
public class ApproveRemovedMappingsActivity implements StartupActivity {
  @Override
  public void runActivity(final Project project) {
    final Map<FileNameMatcher,Pair<FileType,Boolean>> map = ((FileTypeManagerImpl)FileTypeManager.getInstance()).getRemovedMappings();
    if (!map.isEmpty()) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          for (Map.Entry<FileNameMatcher, Pair<FileType, Boolean>> entry : map.entrySet()) {
            if (entry.getValue().getSecond()) {
              continue;
            }
            final FileNameMatcher matcher = entry.getKey();
            final FileType fileType = entry.getValue().getFirst();
            if (Messages.showYesNoDialog(project, "Do you want to re-assign " + matcher.getPresentableString() +
                                                  " files to " + fileType.getName() + "?",
                                         "File Extension Recognized", Messages.getQuestionIcon()) == Messages.YES) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  FileTypeManager.getInstance().associate(fileType, matcher);
                }
              });
            }
            else {
              entry.setValue(Pair.create(fileType, true));
            }
          }
        }
      });
    }
  }
}
