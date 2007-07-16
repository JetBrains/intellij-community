/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.idea.eclipse.EclipseProjectModel;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.util.List;

public class EclipseProjectOpenProcessor extends ProjectOpenProcessor {
  public EclipseProjectOpenProcessor(final EclipseImportBuilder builder) {
    super(builder);
  }

  public EclipseImportBuilder getBuilder() {
    return (EclipseImportBuilder)super.getBuilder();
  }

  public boolean canOpenProject(final VirtualFile file) {
    final String name = file.getName();
    return name.equals(EclipseXml.CLASSPATH_FILE) || name.equals(EclipseXml.PROJECT_FILE);
  }

  public boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    //noinspection ConstantConditions
    getBuilder().setRootDirectory(file.getParent().getPath());

    final List<EclipseProjectModel> projects = getBuilder().getList();
    if (projects.size() != 1) {
      return false;
    }
    getBuilder().setList(projects);
    wizardContext.setProjectName(projects.get(0).getName());
    return true;
  }
}