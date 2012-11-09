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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class GroovySdkWizardStepBase extends ModuleWizardStep {
  private LibraryOptionsPanel myPanel;
  private final LibrariesContainer myLibrariesContainer;
  private boolean myDownloaded;
  private LibraryCompositionSettings myLibraryCompositionSettings;
  @Nullable
  private final MvcFramework myFramework;

  public GroovySdkWizardStepBase(@Nullable final MvcFramework framework, WizardContext wizardContext) {
    final Project project = wizardContext.getProject();
    myLibrariesContainer = LibrariesContainerFactory.createContainer(project);
    myFramework = framework;
  }

  protected ModuleBuilder.ModuleConfigurationUpdater createModuleConfigurationUpdater() {
    return new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        if (myLibraryCompositionSettings != null) {
          myLibraryCompositionSettings.addLibraries(rootModel, new ArrayList<Library>(), myLibrariesContainer);
        }
        module.putUserData(MvcFramework.CREATE_APP_STRUCTURE, Boolean.TRUE);
      }
    };
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
    }
  }

  @Override
  public JComponent getComponent() {
    final JComponent component = getPanel().getMainPanel();
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.NORTH);

    final JLabel caption = new JLabel("Please specify " + (myFramework == null ? "Groovy" : myFramework.getDisplayName()) + " SDK");
    caption.setBorder(new EmptyBorder(0, 0, 10, 0));

    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setBorder(new CompoundBorder(new EtchedBorder(), new EmptyBorder(5, 5, 5, 5)));
    mainPanel.add(caption, BorderLayout.NORTH);
    mainPanel.add(panel, BorderLayout.CENTER);
    return mainPanel;
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch." + (myFramework == null ? "groovy" : myFramework.getFrameworkName().toLowerCase());
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    if (finishChosen && !myDownloaded && myLibraryCompositionSettings != null) {
      if (myLibraryCompositionSettings.downloadFiles(getPanel().getMainPanel())) {
        myDownloaded = true;
      }
    }
  }

  @Override
  public void updateDataModel() {
    myLibraryCompositionSettings = getPanel().apply();
  }

  private synchronized LibraryOptionsPanel getPanel() {
    if (myPanel == null) {
      final GroovyLibraryDescription libraryDescription = myFramework == null ? new GroovyLibraryDescription() : myFramework.createLibraryDescription();
      final String basePath = getBasePath();
      final String baseDirPath = basePath != null ? FileUtil.toSystemIndependentName(basePath) : "";
      myPanel = new LibraryOptionsPanel(libraryDescription, baseDirPath, FrameworkLibraryVersionFilter.ALL, myLibrariesContainer, false);
    }
    return myPanel;
  }

  @Nullable
  protected abstract String getBasePath();
}
