package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;

public class AllInPackageConfigurationProducer extends JUnitConfigurationProducer {
  private PsiPackage myPackage = null;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPackage = checkPackage(element);
    if (myPackage == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final JUnitConfiguration configuration = (JUnitConfiguration)settings.getConfiguration();
    final JUnitConfiguration.Data data = configuration.getPersistentData();
    data.PACKAGE_NAME = myPackage.getQualifiedName();
    data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    if (data.getScope() != TestSearchScope.WHOLE_PROJECT) {
      final Module predefinedModule = configuration.getConfigurationModule().getModule();
      if (predefinedModule == null) {
        Module module = null;
        if (element instanceof PsiDirectory) {
          module = VfsUtil.getModuleForFile(project, ((PsiDirectory)element).getVirtualFile());
        }
        if (module != null) {
          configuration.setModule(module);
        }
        else {
          data.setScope(TestSearchScope.WHOLE_PROJECT);
        }
      }
    } else {
      final RunnerAndConfigurationSettingsImpl template =
        ((RunManagerImpl)context.getRunManager()).getConfigurationTemplate(getConfigurationFactory());
      final Module selectedModule = (Module)context.getDataContext().getData(DataConstants.MODULE);
      if (selectedModule != null) {
        final ModuleBasedConfiguration templateConfiguration = (ModuleBasedConfiguration)template.getConfiguration();
        if (templateConfiguration.getConfigurationModule().getModule() == null) {
          configuration.setModule(selectedModule);
          data.setScope(TestSearchScope.SINGLE_MODULE);
        }
      }
    }
    configuration.setGeneratedName();
    return settings;
  }

  private static PsiPackage checkPackage(final PsiElement element) {
    final Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiPackage) {
      final PsiPackage aPackage = (PsiPackage)element;
      final PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
      for (final PsiDirectory directory : directories) {
        if (isSource(directory, fileIndex)) return aPackage;
      }
      return null;
    }
    else if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      return isSource(directory, fileIndex) ? directory.getPackage() : null;
    }
    else {
      return null;
    }
  }

  private static boolean isSource(final PsiDirectory directory, final ProjectFileIndex fileIndex) {
    final VirtualFile virtualFile = directory.getVirtualFile();
    return fileIndex.getSourceRootForFile(virtualFile) != null;
  }

  public PsiElement getSourceElement() {
    return myPackage;
  }
}
