/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;
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
  private final int mySelectionMode;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;

  public SelectCvsElementStep(String title, CvsWizard wizard,
                              Project project,
                              SelectCVSConfigurationStep selectCVSConfigurationStep,
                              boolean allowRootSelection,
                              int selectionMode,
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
    return myCvsTree.getCurrentSelection().length > 0;
  }

  private boolean isLogged(final CvsRootConfiguration selectedConfiguration) {
    final Ref<Boolean> errors = new Ref<Boolean>();
    final LoginPerformer performer = new LoginPerformer(
      myProject, Collections.<CvsEnvironment>singletonList(selectedConfiguration),
      new Consumer<VcsException>() {
        @Override
        public void consume(VcsException e) {
          errors.set(Boolean.TRUE);
        }
      });
    try {
      final boolean logged = performer.loginAll(false);
      return logged && errors.isNull();
    } catch (CvsRootException e) {
      Messages.showErrorDialog(e.getMessage(), CvsBundle.message("error.title.invalid.cvs.root"));
      return false;
    }
  }

  @Override
  public boolean preNextCheck() {
    CvsRootConfiguration selectedConfiguration = mySelectCVSConfigurationStep.getSelectedConfiguration();
    final boolean logged = isLogged(selectedConfiguration);
    if (logged) {
      myCvsTree.setCvsRootConfiguration(selectedConfiguration);
    }
    return logged;
  }

  @Override
  public boolean setActive() {
    return true;
  }

  @Override
  protected void dispose() {
    if (myCvsTree != null) {
      myCvsTree.deactivated();
    }
  }

  public CvsElement getSelectedCvsElement() {
    CvsElement[] selection = myCvsTree.getCurrentSelection();
    if (selection.length == 0) return null;
    return selection[0];
  }

  @Override
  protected JComponent createComponent() {
    myCvsTree = new CvsTree(myProject, myAllowRootSelection, mySelectionMode, myShowModules, myShowFiles, new Consumer<VcsException>() {
      @Override
      public void consume(VcsException e) {
        Messages.showErrorDialog(e.getMessage(), CvsBundle.message("error.title.cvs.error"));
      }
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
  public Component getPreferredFocusedComponent() {
    return myCvsTree.getTree();
  }

}
