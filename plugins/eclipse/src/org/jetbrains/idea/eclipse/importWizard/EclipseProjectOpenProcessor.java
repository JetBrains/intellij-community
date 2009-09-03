/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;

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

  public boolean canOpenProject(final VirtualFile file) {
    return super.canOpenProject(file) && isEclipseProject(file);
  }

  private static boolean isEclipseProject(final VirtualFile file) {
    if (file.getName().equals(EclipseXml.DOT_CLASSPATH_EXT)) return true;
    final VirtualFile dir = file.getParent();
    return dir != null && dir.findChild(EclipseXml.DOT_CLASSPATH_EXT) != null;
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