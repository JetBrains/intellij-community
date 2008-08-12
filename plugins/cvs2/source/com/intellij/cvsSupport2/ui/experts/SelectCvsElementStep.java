package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsTree;
import com.intellij.cvsSupport2.cvsBrowser.LoginAbortedException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCvsElementStep extends WizardStep {
  private CvsTree myCvsTree;
  private CvsRootConfiguration myConfiguration;
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final Project myProject;
  private final boolean myShowFiles;
  private final int mySelectionMode;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;

  public SelectCvsElementStep(String title, CvsWizard wizard,
                              Project project,
                              SelectCVSConfigurationStep selectCVSConfigurationStep,
                              boolean showFiles,
                              int selectionMode,
                              boolean allowRootSelection, boolean showModules) {
    super(title, wizard);
    myShowModules = showModules;
    mySelectCVSConfigurationStep = selectCVSConfigurationStep;
    myProject = project;
    myShowFiles = showFiles;
    mySelectionMode = selectionMode;
    myAllowRootSelection = allowRootSelection;
    init();
  }

  public boolean nextIsEnabled() {
    return myCvsTree.getCurrentSelection().length > 0;
  }

  public boolean setActive() {
    CvsRootConfiguration selectedConfiguration =
      mySelectCVSConfigurationStep.getSelectedConfiguration();
    if (myCvsTree == null || !Comparing.equal(myConfiguration, selectedConfiguration)) {
      myConfiguration = selectedConfiguration;
      if (myConfiguration == null) return false;
      if (myCvsTree != null) myCvsTree.dispose();
      myCvsTree =
      new CvsTree(myConfiguration, myProject, myShowFiles, mySelectionMode, myAllowRootSelection, myShowModules);
      getStepComponent().removeAll();
      getStepComponent().add(myCvsTree, BorderLayout.CENTER);
      try {
        myCvsTree.init();
      }
      catch (LoginAbortedException ex) {
        return false;
      }

      myCvsTree.addSelectionObserver(new Observer() {
        public void update(Observable o, Object arg) {
          if (CvsTree.SELECTION_CHANGED.equals(arg)) {
            getWizard().updateStep();
          }
          else if (CvsTree.LOGIN_ABORTED.equals(arg)) {
            myCvsTree = null;
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                getWizard().goToPrevious();
              }
            });

          }
        }
      });
    }
    return true;
  }

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

  protected JComponent createComponent() {
    return new JPanel(new BorderLayout());
  }

  public CvsElement[] getSelectedCvsElements() {
    return myCvsTree.getCurrentSelection();
  }

  public Component getPreferredFocusedComponent() {
    return myCvsTree.getTree();
  }

}
