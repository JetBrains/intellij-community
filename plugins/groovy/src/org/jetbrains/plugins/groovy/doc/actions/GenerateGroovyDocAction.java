/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.tools.ant.types.Path;
import org.codehaus.groovy.ant.Groovydoc;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.doc.GenerateGroovyDocDialog;
import org.jetbrains.plugins.groovy.doc.GroovyDocBundle;
import org.jetbrains.plugins.groovy.doc.GroovyDocConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

public final class GenerateGroovyDocAction extends AnAction implements DumbAware {
  @NonNls private static final String INDEX_HTML = "index.html";

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final Module module = DataKeys.MODULE.getData(dataContext);
    if (module == null) return;

    GroovyDocConfiguration configuration = new GroovyDocConfiguration();

    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    if (files.length == 1) {
      configuration.INPUT_DIRECTORY = files[0].getPath();
    }

    final GenerateGroovyDocDialog dialog = new GenerateGroovyDocDialog(project, configuration);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    generateGroovydoc(configuration, project);
  }

  public void update(AnActionEvent event) {
    super.update(event);
    final Presentation presentation = event.getPresentation();
    Module module = LangDataKeys.MODULE.getData(event.getDataContext());

    if (module == null || !LibrariesUtil.hasGroovySdk(module)) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else {
      presentation.setEnabled(true);
      presentation.setVisible(true);
    }
  }

  private static void generateGroovydoc(final GroovyDocConfiguration configuration, final Project project) {
    Runnable groovyDocRun = new Runnable() {
      public void run() {
        Groovydoc groovydoc = new Groovydoc();
        groovydoc.setProject(new org.apache.tools.ant.Project());
        groovydoc.setDestdir(new File(configuration.OUTPUT_DIRECTORY));
        groovydoc.setPrivate(configuration.OPTION_IS_PRIVATE);
        groovydoc.setUse(configuration.OPTION_IS_USE);
        groovydoc.setWindowtitle(configuration.WINDOW_TITLE);

        final Path path = new Path(new org.apache.tools.ant.Project());
        path.setPath(configuration.INPUT_DIRECTORY);
        groovydoc.setSourcepath(path);

        String packages = "";
        for (int i = 0; i < configuration.PACKAGES.length; i++) {
          final String s = configuration.PACKAGES[i];
          if ("".equals(s)) continue;

          if (i > 0) {
            packages += ",";
          }

          packages += s;
        }
        groovydoc.setPackagenames(packages);

        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText(GroovyDocBundle.message("groovy.doc.progress.indication.text"));
        groovydoc.execute();
      }
    };

    ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(groovyDocRun, GroovyDocBundle.message("groovy.documentaion.generating"), false, project);

    if (configuration.OPEN_IN_BROWSER) {
      File url = new File(configuration.OUTPUT_DIRECTORY, INDEX_HTML);
      if (url.exists()) {
        BrowserUtil.launchBrowser(url.getPath());
      }
    }
  }
}
