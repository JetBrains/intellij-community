/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.eclipse.export;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.idea.eclipse.ConversionException;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;
import org.jetbrains.idea.eclipse.conversion.EclipseUserLibrariesHelper;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExportEclipseProjectsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#" + ExportEclipseProjectsAction.class.getName());

  public void update(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    project.save(); // to flush iml files

    final List<Module> modules = new ArrayList<Module>();
    final List<Module> incompatibleModules = new ArrayList<Module>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID.equals(ClassPathStorageUtil.getStorageType(module))) {
        try {
          ClasspathStorage.getProvider(JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID).assertCompatible(ModuleRootManager.getInstance(module));
          modules.add(module);
        }
        catch (ConfigurationException e1) {
          incompatibleModules.add(module);
        }
      }
    }

    //todo suggest smth with hierarchy modules
    if (!incompatibleModules.isEmpty()) {
      if (Messages.showOkCancelDialog(project, "<html><body>Eclipse incompatible modules found:<ul><br><li>" +
                                               StringUtil.join(incompatibleModules, new Function<Module, String>() {
                                                 public String fun(Module module) {
                                                   return module.getName();
                                                 }
                                               }, "<br><li>") +
                                               "</ul><br>Would you like to proceed and possibly lose your configurations?</body></html>",
                                      "Eclipse Incompatible Modules Found", Messages.getWarningIcon()) != Messages.OK) {
        return;
      }
    }
    else if (modules.isEmpty()) {
      Messages.showInfoMessage(project, EclipseBundle.message("eclipse.export.nothing.to.do"),
                               EclipseBundle.message("eclipse.export.dialog.title"));
      return;
    }

    modules.addAll(incompatibleModules);
    final ExportEclipseProjectsDialog dialog = new ExportEclipseProjectsDialog(project, modules);
    dialog.show();
    if (dialog.isOK()) {
      if (dialog.isLink()) {
        for (Module module : dialog.getSelectedModules()) {
          ClasspathStorage.setStorageType(ModuleRootManager.getInstance(module), JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID);
        }
      }
      else {
        for (Module module : dialog.getSelectedModules()) {
          final ModuleRootModel model = ModuleRootManager.getInstance(module);
          final VirtualFile[] contentRoots = model.getContentRoots();         //todo
          final String storageRoot =
            contentRoots.length == 1 ? contentRoots[0].getPath() : ClasspathStorage.getStorageRootFromOptions(module);
          try {
            final Element classpathEleemnt = new Element(EclipseXml.CLASSPATH_TAG);

            final EclipseClasspathWriter classpathWriter = new EclipseClasspathWriter(model);
            classpathWriter.writeClasspath(classpathEleemnt, null);
            final File classpathFile = new File(storageRoot, EclipseXml.CLASSPATH_FILE);
            if (!FileUtil.createIfDoesntExist(classpathFile)) continue;
            EclipseJDOMUtil.output(new Document(classpathEleemnt), classpathFile, project);

            final Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
            if (IdeaSpecificSettings.writeIDEASpecificClasspath(ideaSpecific, model)) {
              final File emlFile = new File(storageRoot, module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
              if (!FileUtil.createIfDoesntExist(emlFile)) continue;
              EclipseJDOMUtil.output(new Document(ideaSpecific), emlFile, project);
            }

            DotProjectFileHelper.saveDotProjectFile(module, storageRoot);
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
        }
      }
      try {
        EclipseUserLibrariesHelper.appendProjectLibraries(project, dialog.getUserLibrariesFile());
      }
      catch (IOException e1) {
        LOG.error(e1);
      }
      project.save();
    }
  }
}
