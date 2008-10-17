package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.groovy.doc.GroovyDocGenerationManager;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.grails.config.GrailsFacetUtil;

public final class GenerateGroovyDocAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    GroovyDocGenerationManager.getInstance(project).generateGroovydoc(dataContext);
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