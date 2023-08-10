// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.util.ArrayList;

public final class GroovySdkForNewModuleWizardStep extends ModuleWizardStep {

  private final String myBasePath;
  private final LibrariesContainer myLibrariesContainer;
  private final Disposable myDisposable;
  private final @Nullable ModuleWizardStep myJavaStep;

  private LibraryOptionsPanel myPanel;
  private LibraryCompositionSettings myLibraryCompositionSettings;
  private boolean myDownloaded;

  GroovySdkForNewModuleWizardStep(@NotNull ModuleBuilder moduleBuilder, @NotNull SettingsStep settingsStep) {
    myBasePath = moduleBuilder.getContentEntryPath();
    WizardContext wizardContext = settingsStep.getContext();
    myLibrariesContainer = wizardContext.getModulesProvider() == null
                           ? LibrariesContainerFactory.createContainer(wizardContext.getProject())
                           : LibrariesContainerFactory.createContainer(wizardContext, wizardContext.getModulesProvider());
    myDisposable = wizardContext.getDisposable();
    moduleBuilder.addModuleConfigurationUpdater(createModuleConfigurationUpdater());
    myJavaStep = JavaModuleType.getModuleType().modifyProjectTypeStep(settingsStep, moduleBuilder);
    settingsStep.addSettingsField(GroovyBundle.message("groovy.library.label"), getPanel().getSimplePanel());
  }

  private ModuleBuilder.ModuleConfigurationUpdater createModuleConfigurationUpdater() {
    return new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        if (myLibraryCompositionSettings != null) {
          myLibraryCompositionSettings.addLibraries(rootModel, new ArrayList<>(), myLibrariesContainer);
        }
      }
    };
  }

  @Override
  public JComponent getComponent() {
    return getPanel().getMainPanel();
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.groovy";
  }

  @Override
  public boolean validate() throws ConfigurationException {
    return super.validate() && (myJavaStep == null || myJavaStep.validate());
  }

  @Override
  public void updateDataModel() {
    myLibraryCompositionSettings = getPanel().apply();
    if (myJavaStep != null) {
      myJavaStep.updateDataModel();
    }
  }

  @Override
  public void _commit(boolean finishChosen) {
    if (finishChosen && !myDownloaded && myLibraryCompositionSettings != null) {
      if (myLibraryCompositionSettings.downloadFiles(getPanel().getMainPanel())) {
        myDownloaded = true;
      }
    }
  }

  private LibraryOptionsPanel getPanel() {
    if (myPanel == null) {
      final GroovyLibraryDescription libraryDescription = new GroovyLibraryDescription();
      final String baseDirPath = myBasePath != null ? FileUtil.toSystemIndependentName(myBasePath) : "";
      myPanel = new LibraryOptionsPanel(libraryDescription, baseDirPath, FrameworkLibraryVersionFilter.ALL, myLibrariesContainer, false);
      Disposer.register(myDisposable, myPanel);
    }
    return myPanel;
  }
}
