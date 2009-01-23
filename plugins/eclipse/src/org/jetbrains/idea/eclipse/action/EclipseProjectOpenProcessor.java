/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.find.EclipseProjectFinder;

import java.util.List;

public class EclipseProjectOpenProcessor extends ProjectOpenProcessorBase {
  public EclipseProjectOpenProcessor(final EclipseImportBuilder builder) {
    super(builder);
  }

  public EclipseImportBuilder getBuilder() {
    return (EclipseImportBuilder)super.getBuilder();
  }

  @Nullable
  public String[] getSupportedExtensions() {
    return new String[] {EclipseXml.CLASSPATH_FILE, EclipseXml.PROJECT_FILE};
  }

  public boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    //noinspection ConstantConditions
    getBuilder().setRootDirectory(file.getParent().getPath());

    final List<String> projects = getBuilder().getList();
    if (projects.size() != 1) {
      return false;
    }
    getBuilder().setList(projects);
    wizardContext.setProjectName(EclipseProjectFinder.findProjectName(projects.get(0)));
    return true;
  }
}