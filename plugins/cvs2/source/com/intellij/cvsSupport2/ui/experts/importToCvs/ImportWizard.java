package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsoperations.cvsImport.ImportDetails;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.SelectCVSConfigurationStep;
import com.intellij.cvsSupport2.ui.experts.SelectCvsElementStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.tree.TreeSelectionModel;

/**
 * author: lesya
 */
public class ImportWizard extends CvsWizard{
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final SelectCvsElementStep mySelectCvsElementStep;
  private SelectImportLocationStep mySelectImportLocationStep;
  private CustomizeKeywordSubstitutionStep myKeywordSubstitutionStep;
  private ImportSettingsStep mySettingsStep;

  public ImportWizard(Project project, VirtualFile selectedFile) {
    super("Import into CVS", project);
    ImportConfiguration importConfig = ImportConfiguration.getInstanse();
    mySelectCVSConfigurationStep = new SelectCVSConfigurationStep(project, this);
    mySelectCvsElementStep = new SelectCvsElementStep("Select Directory to Import into",this,
                                                      project,
                                                      mySelectCVSConfigurationStep,
                                                      false,TreeSelectionModel.SINGLE_TREE_SELECTION, true, false);

    mySelectImportLocationStep = new SelectImportLocationStep(
                                            "Select Import Directory",
                                             this,
                                            project,
                                            selectedFile);

    myKeywordSubstitutionStep = new CustomizeKeywordSubstitutionStep("Customize Keyword Substitutions",
                this, importConfig);

    mySettingsStep = new ImportSettingsStep(this, mySelectImportLocationStep, importConfig);

    addStep(mySelectCVSConfigurationStep);
    addStep(mySelectCvsElementStep);
    addStep(mySelectImportLocationStep);
    addStep(myKeywordSubstitutionStep);
    addStep(mySettingsStep);

    init();
  }

  public ImportDetails createImportDetails() {
    CvsElement module = mySelectCvsElementStep.getSelectedCvsElement();
    String moduleName = mySettingsStep.getModuleName();
    String importModuleName = module.getName().equals(".") ? moduleName : module.getElementPath() + "/" + moduleName;

    return new ImportDetails(mySelectImportLocationStep.getSelectedFile(),
                             mySettingsStep.getVendor(),
                             mySettingsStep.getReleaseTag(),
                             mySettingsStep.getLogMessage(),
                             importModuleName,
                             mySelectCVSConfigurationStep.getSelectedConfiguration(),
                             myKeywordSubstitutionStep.getFileExtensions(),
                             mySelectImportLocationStep.getIgnoreFileFilter());
  }
  protected String getHelpID() {
    return "cvs.import";
  }

}
