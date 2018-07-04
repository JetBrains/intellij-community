// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreatePackageInfoAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
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

  private static class LocalMissingPackageInfoInspection extends LocalMissingPackageInfoInspectionBase {

    public LocalMissingPackageInfoInspection(MissingPackageInfoInspectionBase settingsDelegate) {
      super(settingsDelegate);
    }

    @Nullable
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
      return new InspectionGadgetsFix() {
        @NotNull
        @Override
        public String getFamilyName() {
          return "Create 'package-info.java'";
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
          DataManager.getInstance()
                     .getDataContextFromFocusAsync()
                     .onSuccess(context -> {
                       final AnActionEvent event = new AnActionEvent(null, context, "", new Presentation(), ActionManager.getInstance(), 0);
                       new CreatePackageInfoAction().actionPerformed(event);
                     });
        }
      };
    }
  }
}
