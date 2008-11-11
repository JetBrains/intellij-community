package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.grails.config.GrailsFacetUtil;
import org.jetbrains.plugins.grails.util.GrailsUtils;
import org.jetbrains.plugins.groovy.doc.GenerateGroovyDocDialog;
import org.jetbrains.plugins.groovy.doc.GroovyDocConfiguration;
import org.jetbrains.plugins.groovy.doc.GroovyDocGenerationManager;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

public final class GenerateGroovyDocAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final Module module = DataKeys.MODULE.getData(dataContext);
    if (module == null) return;

    //final PsiDirectory directory = getDirectoryFromContext(dataContext);
    //final VirtualFile directory = GrailsUtils.findModuleGrailsAppDir(module);
    final VirtualFile directory = GrailsUtils.findGrailsAppRoot(module);
    GroovyDocConfiguration configuration = new GroovyDocConfiguration();

    if (directory != null) {
      configuration.INPUT_DIRECTORY = directory.getPath();
    }

    final GenerateGroovyDocDialog dialog = new GenerateGroovyDocDialog(project, configuration);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    GroovyDocGenerationManager.getInstance(project).generateGroovydoc(configuration);
  }

  public void update(AnActionEvent event) {
    super.update(event);
    final Presentation presentation = event.getPresentation();
    final DataContext context = event.getDataContext();
    Module module = (Module)context.getData(DataKeys.MODULE.getName());

    if (!GroovyUtils.isSuitableModule(module) || !GrailsFacetUtil.hasGrailsSupport(module)) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }
}