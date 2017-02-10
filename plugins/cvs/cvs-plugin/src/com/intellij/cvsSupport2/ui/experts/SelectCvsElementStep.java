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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootException;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsTree;
import com.intellij.cvsSupport2.cvsoperations.common.LoginPerformer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCvsElementStep extends WizardStep {
  private CvsTree myCvsTree;
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final Project myProject;
  private final boolean myShowFiles;
  @JdkConstants.TreeSelectionMode private final int mySelectionMode;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;
  private final Ref<Boolean> myErrors = new Ref<>();

  public SelectCvsElementStep(String title, CvsWizard wizard,
                              Project project,
                              SelectCVSConfigurationStep selectCVSConfigurationStep,
                              boolean allowRootSelection,
                              @JdkConstants.TreeSelectionMode int selectionMode,
                              boolean showModules,
                              boolean showFiles) {
    super(title, wizard);
    myShowModules = showModules;
    mySelectCVSConfigurationStep = selectCVSConfigurationStep;
    myProject = project;
    myShowFiles = showFiles;
    mySelectionMode = selectionMode;
    myAllowRootSelection = allowRootSelection;
    init();
  }

  @Override
  public boolean nextIsEnabled() {
    return myCvsTree.getCurrentSelection().length > 0 && myErrors.isNull();
  }

  private boolean isLogged(final CvsRootConfiguration selectedConfiguration) {
    myErrors.set(null);
    final LoginPerformer performer = new LoginPerformer(
      myProject, Collections.<CvsEnvironment>singletonList(selectedConfiguration),
      e -> myErrors.set(Boolean.TRUE));
    try {
      final boolean logged = performer.loginAll(false);
      return logged && myErrors.isNull();
    } catch (CvsRootException e) {
      Messages.showErrorDialog(e.getMessage(), CvsBundle.message("error.title.invalid.cvs.root"));
      return false;
    }
  }

  @Override
  public boolean preNextCheck() {
    final CvsRootConfiguration selectedConfiguration = mySelectCVSConfigurationStep.getSelectedConfiguration();
    if (selectedConfiguration == null) {
      return false;
    }
    final boolean logged = isLogged(selectedConfiguration);
    if (logged) {
      myCvsTree.setCvsRootConfiguration((CvsRootConfiguration)selectedConfiguration.clone());
    }
    return logged;
  }

  @Override
  protected void dispose() {
    if (myCvsTree != null) {
      myCvsTree.deactivated();
    }
  }

  @Nullable
  public CvsElement getSelectedCvsElement() {
    final CvsElement[] selection = myCvsTree.getCurrentSelection();
    if (selection.length == 0) return null;
    return selection[0];
  }

  @Override
  protected JComponent createComponent() {
    myCvsTree = new CvsTree(myProject, myAllowRootSelection, mySelectionMode, myShowModules, myShowFiles, e -> {
      myErrors.set(Boolean.TRUE);
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showErrorDialog(e.getMessage(), CvsBundle.message("error.title.cvs.error")), ModalityState.any());
    });
    myCvsTree.init();
    myCvsTree.addSelectionObserver(new Observer() {
      @Override
      public void update(Observable o, Object arg) {
        if (CvsTree.SELECTION_CHANGED.equals(arg)) {
          getWizard().updateStep();
        }
      }
    });
    return myCvsTree;
  }

  public CvsElement[] getSelectedCvsElements() {
    return myCvsTree.getCurrentSelection();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCvsTree.getTree();
  }
}
