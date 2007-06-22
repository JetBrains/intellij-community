package org.jetbrains.idea.eclipse.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;

import java.util.ArrayList;
import java.util.List;

public class ExportEclipseProjectsAction extends AnAction {
  public void update(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled( project != null );
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if ( project == null ) return;
    project.save(); // to flush iml files

    List<Module> modules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!EclipseClasspathStorageProvider.ID.equals(ClasspathStorage.getStorageType(module)) &&
          EclipseClasspathStorageProvider.isCompatible(ModuleRootManager.getInstance(module))) {
        modules.add(module);
      }
    }

    if (modules.isEmpty()){
      Messages.showInfoMessage(project, EclipseBundle.message("eclipse.export.nothing.to.do"), EclipseBundle.message("eclipse.export.dialog.title"));
      return;
    }

    final ExportEclipseProjectsDialog dialog = new ExportEclipseProjectsDialog(project, modules);
    dialog.show ();
    if(dialog.isOK()){
      if (dialog.isLink()) {
        for (Module module : dialog.getSelectedModules()) {
          ClasspathStorage.setStorageType(module, EclipseClasspathStorageProvider.ID);
        }
      }
      else {
        EclipseProjectExporter.convertWithProgress(project, dialog.getSelectedModules());
      }
      project.save();
    }
  }
}
