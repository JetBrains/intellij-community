package org.jetbrains.idea.eclipse.export;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.conversion.ConversionException;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExportEclipseProjectsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + ExportEclipseProjectsAction.class.getName());

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled( project != null );
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if ( project == null ) return;
    project.save(); // to flush iml files

    List<Module> modules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!EclipseClasspathStorageProvider.ID.equals(ClasspathStorage.getStorageType(module)) &&
          EclipseClasspathStorageProvider.isCompatible(ModuleRootManager.getInstance(module)) &&
          EclipseClasspathStorageProvider.hasIncompatibleLibrary(ModuleRootManager.getInstance(module)) == null) {
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
          final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          ClasspathStorage.setStorageType(model, EclipseClasspathStorageProvider.ID);
          model.dispose();
        }
      }
      else {
        for (Module module : dialog.getSelectedModules()) {
          final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          final String storageRoot = ClasspathStorage.getStorageRootFromOptions(module);
          try {
            final Element classpathEleemnt = new Element(EclipseXml.CLASSPATH_TAG);

            final EclipseClasspathWriter classpathWriter = new EclipseClasspathWriter(model);
            classpathWriter.writeClasspath(classpathEleemnt, null);
            final File classpathFile = new File(storageRoot, EclipseXml.CLASSPATH_FILE);
            if (!classpathFile.exists()) {
              if (!classpathFile.createNewFile()) continue;
            }
            EclipseJDOMUtil.output(new Document(classpathEleemnt), classpathFile);

            final Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
            if (classpathWriter.writeIDEASpecificClasspath(ideaSpecific)) {
              final File emlFile = new File(storageRoot, module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
              if (!emlFile.exists()) {
                if (!emlFile.createNewFile()) continue;
              }
              EclipseJDOMUtil.output(new Document(ideaSpecific), emlFile);
            }

            try {
              final Document doc;
              if (module.getModuleType() instanceof JavaModuleType) {
                doc = JDOMUtil.loadDocument(getClass().getResource("template.project.xml"));
              } else {
                doc = JDOMUtil.loadDocument(getClass().getResource("template.empty.project.xml"));
              }

              final Element nameElement = doc.getRootElement().getChild(EclipseXml.NAME_TAG);
              nameElement.setText(module.getName());

              final File projectFile = new File(storageRoot, EclipseXml.PROJECT_FILE);
              if (!projectFile.exists()) {
                if (!projectFile.createNewFile()) continue;
              }
              EclipseJDOMUtil.output(doc, projectFile);
            }
            catch (JDOMException e1) {
              LOG.error(e1);
            }
          }
          catch (ConversionException e1) {
            LOG.error(e1);
          }
          catch (IOException e1) {
            LOG.error(e1);
          }
          catch (WriteExternalException e1) {
            LOG.error(e1);
          }
          finally {
            model.dispose();
          }
        }
      }
      project.save();
    }
  }
}
