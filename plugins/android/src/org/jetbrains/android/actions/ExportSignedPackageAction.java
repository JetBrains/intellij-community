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

package org.jetbrains.android.actions;

import com.intellij.CommonBundle;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.exportSignedPackage.CheckModulePanel;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageAction extends AnAction {
  public ExportSignedPackageAction() {
    super(AndroidBundle.message("android.export.signed.package.action.text"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }

  private static void makeProjectIfNeccessaryAndRun(final Project project, final Runnable afterAction) {
    final CompilerManager manager = CompilerManager.getInstance(project);
    final CompileScope compileScope = manager.createProjectCompileScope(project);
    if (!manager.isUpToDate(compileScope)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.export.signed.package.make.question"),
                                                      AndroidBundle.message("android.export.signed.package.action.text"),
                                                      Messages.getQuestionIcon());
          if (result == 0) {
            manager.make(compileScope, new CompileStatusNotification() {
              public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
                if (!aborted && errors == 0) {
                  afterAction.run();
                }
              }
            });
          }
          else {
            afterAction.run();
          }
        }
      });
    }
    else {
      ApplicationManager.getApplication().invokeLater(afterAction);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    assert project != null;
    final Runnable exportRunnable = new Runnable() {
      public void run() {
        List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
        assert facets.size() > 0;
        if (facets.size() == 1) {
          if (!checkFacet(facets.get(0))) return;
        }
        ExportSignedPackageWizard wizard = new ExportSignedPackageWizard(project, facets);
        wizard.show();
      }
    };
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        makeProjectIfNeccessaryAndRun(project, exportRunnable);
      }
    });
  }

  private static boolean checkFacet(final AndroidFacet facet) {
    final CheckModulePanel panel = new CheckModulePanel();
    panel.updateMessages(facet);
    final boolean hasError = panel.hasError();
    if (hasError || panel.hasWarnings()) {
      DialogWrapper dialog = new DialogWrapper(facet.getModule().getProject()) {
        {
          if (!hasError) {
            setOKButtonText("Continue");
          }
          init();
        }

        @Override
        protected Action[] createActions() {
          if (hasError) {
            return new Action[]{getOKAction()};
          }
          return super.createActions();
        }

        @Override
        protected JComponent createCenterPanel() {
          return panel;
        }
      };
      dialog.setTitle(hasError ? CommonBundle.getErrorTitle() : CommonBundle.getWarningTitle());
      dialog.show();
      return !hasError && dialog.isOK();
    }
    return true;
  }
}
