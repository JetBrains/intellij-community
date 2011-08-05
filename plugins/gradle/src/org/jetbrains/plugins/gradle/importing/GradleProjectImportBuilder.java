package org.jetbrains.plugins.gradle.importing;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleIcons;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * GoF builder for gradle-backed projects.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:29 PM
 */
@SuppressWarnings("MethodMayBeStatic")
public class GradleProjectImportBuilder extends ProjectImportBuilder<GradleProject> {

  private static final String GRADLE_IMPORT_ROOT_KEY = "gradle.import.root";
  
  private final GradleProjectParser myProjectParser = new GradleProjectParser();
  private GradleProject myProject;
  private File myProjectFile;
  
  @Override
  public String getName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.GRADLE_ICON;
  }

  @Override
  public List<GradleProject> getList() {
    // TODO den implement
    return null;
  }

  @Override
  public boolean isMarked(GradleProject element) {
    // TODO den implement
    return false;
  }

  @Override
  public void setList(List<GradleProject> gradleProjects) throws ConfigurationException {
    // TODO den implement
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
    // TODO den implement
  }

  @Override
  public List<Module> commit(Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel)
  {
    // TODO den implement
    return null;
  }

  /**
   * Allows to retrieve initial path to use during importing gradle projects
   * 
   * @param context  current wizard context (if the one is available)
   * @return         start path to use during importing gradle projects
   */
  @NotNull
  public String getRootDirectoryPath(@Nullable WizardContext context) {
    String result = PropertiesComponent.getInstance().getValue(GRADLE_IMPORT_ROOT_KEY, "");
    if (new File(result).isDirectory()) {
      return result;
    }
    if (context == null) {
      return "";
    }
    return context.getProjectFileDirectory();
  }

  /**
   * Is called to indicate that user changed path to the gradle project to import.
   * 
   * @param path      new directory path
   */
  public void setCurrentProjectPath(@NotNull String path) {
    File file = new File(path);
    myProjectFile = file;
    while (file != null && !file.exists()) {
      file = file.getParentFile();
    }
    if (file == null) {
      return;
    } 
    String pathToStore = null;
    if (file.isDirectory()) {
      pathToStore = file.getAbsolutePath();
    }
    else if (file.isFile()) {
      pathToStore = file.getParentFile().getAbsolutePath();
    }
    if (pathToStore != null) {
      PropertiesComponent.getInstance().setValue(GRADLE_IMPORT_ROOT_KEY, pathToStore);
    }
  }

  /**
   * Asks current builder to ensure that target gradle project is defined.
   * 
   * @throws ConfigurationException   if gradle project is not defined and can't be constructed
   */
  public void ensureProjectIsDefined() throws ConfigurationException {
    if (myProjectFile == null) {
      throw new ConfigurationException(GradleBundle.message("gradle.import.text.error.project.undefined"));
    }
    if (myProjectFile.isDirectory()) {
      throw new ConfigurationException(GradleBundle.message("gradle.import.text.error.directory.instead.file"));
    }
    try {
      myProject = myProjectParser.parse(myProjectFile);
    }
    catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage(), GradleBundle.message("gradle.import.text.error.cannot.parse.project"));
    }
  }
}
