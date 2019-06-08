// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.doc.GenerateGroovyDocDialog;
import org.jetbrains.plugins.groovy.doc.GroovyDocBundle;
import org.jetbrains.plugins.groovy.doc.GroovyDocConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

public final class GenerateGroovyDocAction extends AnAction implements DumbAware {
  @NonNls private static final String INDEX_HTML = "index.html";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) return;

    GroovyDocConfiguration configuration = new GroovyDocConfiguration();

    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    if (files.length == 1) {
      configuration.INPUT_DIRECTORY = files[0].getPath();
    }

    final GenerateGroovyDocDialog dialog = new GenerateGroovyDocDialog(project, configuration);
    if (!dialog.showAndGet()) {
      return;
    }

    generateGroovydoc(configuration, project);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    Module module = event.getData(LangDataKeys.MODULE);

    if (module == null || !LibrariesUtil.hasGroovySdk(module)) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setEnabledAndVisible(true);
    }
  }

  private static void generateGroovydoc(final GroovyDocConfiguration configuration, final Project project) {
    Runnable groovyDocRun = () -> {
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
        if (s != null && s.isEmpty()) continue;

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
    };

    ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(groovyDocRun, GroovyDocBundle.message("groovy.documentation.generating"), false, project);

    if (configuration.OPEN_IN_BROWSER) {
      File url = new File(configuration.OUTPUT_DIRECTORY, INDEX_HTML);
      if (url.exists()) {
        BrowserUtil.browse(url);
      }
    }
  }
}
