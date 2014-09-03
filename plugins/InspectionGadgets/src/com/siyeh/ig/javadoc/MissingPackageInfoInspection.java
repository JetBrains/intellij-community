/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreatePackageInfoAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MissingPackageInfoInspection extends MissingPackageInfoInspectionBase {

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalMissingPackageInfoInspection(this);
  }

  private class LocalMissingPackageInfoInspection extends LocalMissingPackageInfoInspectionBase {

    public LocalMissingPackageInfoInspection(MissingPackageInfoInspectionBase settingsDelegate) {
      super(settingsDelegate);
    }

    @Nullable
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
      return new InspectionGadgetsFix() {
        @NotNull
        @Override
        public String getName() {
          return "Create 'package-info.java'";
        }

        @NotNull
        @Override
        public String getFamilyName() {
          return getName();
        }

        @Override
        protected boolean prepareForWriting() {
          return false;
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
          DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
            @Override
            public void consume(DataContext context) {
              final AnActionEvent event = new AnActionEvent(null, context, "", new Presentation(), ActionManager.getInstance(), 0);
              new CreatePackageInfoAction().actionPerformed(event);
            }
          });
        }
      };
    }
  }
}
