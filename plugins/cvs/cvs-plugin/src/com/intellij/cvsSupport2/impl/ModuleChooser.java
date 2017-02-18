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
package com.intellij.cvsSupport2.impl;

import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsFile;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.SelectCVSConfigurationStep;
import com.intellij.cvsSupport2.ui.experts.SelectCvsElementStep;
import com.intellij.openapi.cvsIntegration.CvsModule;
import com.intellij.openapi.cvsIntegration.CvsRepository;
import com.intellij.openapi.project.Project;

import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;

/**
 * author: lesya
 */
public class ModuleChooser extends CvsWizard {
  private final SelectCVSConfigurationStep mySelectCVSConfigurationStep;
  private final SelectCvsElementStep mySelectCvsElementStep;

  public ModuleChooser(Project project, 
                       boolean allowFileSelection,
                       boolean allowMultipleSelection,
                       boolean allowRootSelection,
                       String expertTitle,
                       String selectModulePageTitle) {
    super(expertTitle, project);
    mySelectCVSConfigurationStep = new SelectCVSConfigurationStep(project, this);
    mySelectCvsElementStep = new SelectCvsElementStep(selectModulePageTitle,
                                                      this,
                                                      project,
                                                      mySelectCVSConfigurationStep, allowRootSelection, allowMultipleSelection ?
                                                                          TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION :
                                                                          TreeSelectionModel.SINGLE_TREE_SELECTION, true,
                                                      allowFileSelection);

    addStep(mySelectCVSConfigurationStep);
    addStep(mySelectCvsElementStep);

    init();
  }

  private CvsRepository getSelectedRepository() {
    return mySelectCVSConfigurationStep.getSelectedConfiguration().createCvsRepository();
  }

  public CvsModule[] getSelectedModules() {
    CvsRepository repository = getSelectedRepository();
    CvsElement[] selectedCvsElement = mySelectCvsElementStep.getSelectedCvsElements();
    ArrayList<CvsModule> result = new ArrayList<>();
    for (CvsElement cvsElement : selectedCvsElement) {
      result.add(new CvsModule(repository, cvsElement.getElementPath(), cvsElement instanceof CvsFile));
    }
    return result.toArray(new CvsModule[result.size()]);
  }

}
