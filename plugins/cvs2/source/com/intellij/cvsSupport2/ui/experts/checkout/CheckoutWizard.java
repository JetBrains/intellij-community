package com.intellij.cvsSupport2.ui.experts.checkout;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.SelectCVSConfigurationStep;
import com.intellij.cvsSupport2.ui.experts.SelectCvsElementStep;
import com.intellij.cvsSupport2.ui.experts.SelectLocationStep;
import com.intellij.openapi.project.Project;

import javax.swing.tree.TreeSelectionModel;
import java.io.File;

/**
 * author: lesya
 */
public class CheckoutWizard extends CvsWizard {
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final SelectCvsElementStep mySelectCvsElementStep;
  private final SelectLocationStep mySelectLocationStep;

  private ChooseCheckoutMode myChooseModeStep;

  public CheckoutWizard(final Project project) {
    super("Check Out from CVS Repository", project);
    mySelectCVSConfigurationStep = new SelectCVSConfigurationStep(project, this);
    mySelectCvsElementStep = new SelectCvsElementStep("Select CVS Element to Check Out",
                                                      this, project, mySelectCVSConfigurationStep,
                                                      true, TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, false, true);

    mySelectLocationStep = new MySelectLocationStep(project);
    myChooseModeStep = new ChooseCheckoutMode(this);

    addStep(mySelectCVSConfigurationStep);
    addStep(mySelectCvsElementStep);
    addStep(mySelectLocationStep);

    addStep(myChooseModeStep);

    init();
  }

  protected void doOKAction() {
    CvsApplicationLevelConfiguration config = CvsApplicationLevelConfiguration.getInstance();

    config.MAKE_CHECKED_OUT_FILES_READONLY = myChooseModeStep.getMakeNewFielsReadOnly();
    config.CHECKOUT_PRUNE_EMPTY_DIRECTORIES = myChooseModeStep.getPruneEmptyDirectories();
    config.CHECKOUT_KEYWORD_SUBSTITUTION = myChooseModeStep.getKeywordSubstitution();

    super.doOKAction();
  }


  public CvsElement[] getSelectedElements() {
    return mySelectCvsElementStep.getSelectedCvsElements();
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelectCVSConfigurationStep.getSelectedConfiguration();
  }

  public boolean useAlternativeCheckoutLocation() {
    return myChooseModeStep.useAlternativeCheckoutLocation();
  }

  public File getCheckoutDirectory() {
    return myChooseModeStep.getCheckoutDirectory();
  }

  public File getSelectedLocation() {
    return mySelectLocationStep.getSelectedFile();
  }

  private class MySelectLocationStep extends SelectLocationStep {
    public MySelectLocationStep(Project project) {
      super("Select Check Out Location", CheckoutWizard.this, project);
      init();
    }
  }

  protected String getHelpID() {
    return "cvs.checkOutPrj";
  }

}
