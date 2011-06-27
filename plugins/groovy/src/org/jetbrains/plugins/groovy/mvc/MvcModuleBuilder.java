package org.jetbrains.plugins.groovy.mvc;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder;
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author peter
 */
public class MvcModuleBuilder extends GroovyAwareModuleBuilder {
  private final MvcFramework myFramework;

  protected MvcModuleBuilder(MvcFramework framework, Icon bigIcon) {
    super(framework.getFrameworkName(), framework.getDisplayName() + " Application", "Create a new " + framework.getDisplayName() + " application", bigIcon);
    myFramework = framework;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    final ModuleWizardStep sdkStep = new GroovySdkWizardStep(wizardContext);
    return new ModuleWizardStep[]{ProjectWizardStepFactory.getInstance().createProjectJdkStep(wizardContext), sdkStep};
  }

  private class GroovySdkWizardStep extends ModuleWizardStep {
    private final LibraryOptionsPanel myPanel;
    private final LibrariesContainer myLibrariesContainer;
    private boolean myDownloaded;
    private LibraryCompositionSettings myLibraryCompositionSettings;

    public GroovySdkWizardStep(WizardContext wizardContext) {
      final Project project = wizardContext.getProject();
      final GroovyLibraryDescription libraryDescription = myFramework.createLibraryDescription();
      final String contentEntryPath = getContentEntryPath();
      final String basePath = contentEntryPath != null ? FileUtil.toSystemIndependentName(contentEntryPath) : "";
      myLibrariesContainer = LibrariesContainerFactory.createContainer(project);
      myPanel = new LibraryOptionsPanel(libraryDescription, basePath, null, myLibrariesContainer, false);
      addModuleConfigurationUpdater(new ModuleConfigurationUpdater() {
        @Override
        public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
          if (myLibraryCompositionSettings != null) {
            myLibraryCompositionSettings.addLibraries(rootModel, new ArrayList<Library>(), myLibrariesContainer);
          }
          module.putUserData(MvcFramework.CREATE_APP_STRUCTURE, Boolean.TRUE);
        }
      });
    }

    @Override
    public void disposeUIResources() {
      Disposer.dispose(myPanel);
    }

    @Override
    public JComponent getComponent() {
      final JComponent component = myPanel.getMainPanel();
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(component, BorderLayout.NORTH);

      final JLabel caption = new JLabel("Please specify " + myFramework.getDisplayName() + " SDK");
      caption.setBorder(new EmptyBorder(0, 0, 10, 0));

      final JPanel mainPanel = new JPanel(new BorderLayout());
      mainPanel.setBorder(new CompoundBorder(new EtchedBorder(), new EmptyBorder(5, 5, 5, 5)));
      mainPanel.add(caption, BorderLayout.NORTH);
      mainPanel.add(panel, BorderLayout.CENTER);
      return mainPanel;
    }

    @Override
    public String getHelpId() {
      return "reference.dialogs.new.project.fromScratch." + myFramework.getFrameworkName().toLowerCase();
    }

    @Override
    public void _commit(boolean finishChosen) throws CommitStepException {
      if (finishChosen && !myDownloaded && myLibraryCompositionSettings != null) {
        if (myLibraryCompositionSettings.downloadFiles(myPanel.getMainPanel())) {
          myDownloaded = true;
        }
      }
    }

    @Override
    public void updateDataModel() {
      myLibraryCompositionSettings = myPanel.apply();
    }
  }
}
