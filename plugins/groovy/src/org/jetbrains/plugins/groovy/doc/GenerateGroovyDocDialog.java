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
package org.jetbrains.plugins.groovy.doc;

import com.intellij.CommonBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.io.File;

public final class GenerateGroovyDocDialog extends DialogWrapper {
  private final Project myProject;
  private final GroovyDocConfiguration myConfiguration;
  private final GroovyDocGenerationPanel myPanel;

  public GenerateGroovyDocDialog(Project project, GroovyDocConfiguration configuration) {
    super(project, true);
    myProject = project;
    myConfiguration = configuration;

    setOKButtonText(GroovyDocBundle.message("groovydoc.generate.start.button"));
    setTitle(GroovyDocBundle.message("groovydoc.generate.title"));

    myPanel = new GroovyDocGenerationPanel();
    init();
    myPanel.reset(configuration);
  }

  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    myPanel.apply(myConfiguration);
    if (checkOutputDirectory(myConfiguration.OUTPUT_DIRECTORY) && checkInputDirectory(myConfiguration.INPUT_DIRECTORY)) {
      close(OK_EXIT_CODE);
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.groovydocGeneration");
  }

  private boolean checkOutputDirectory(String outputDirectory) {
    if (outputDirectory == null || outputDirectory.trim().length() == 0) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.not.specified"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }

    File outputDir = new File(outputDirectory);
    if (!outputDir.exists()) {
      int choice = Messages
        .showOkCancelDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.not.exists", outputDirectory),
                            GroovyDocBundle.message("groovydoc.generate.message.title"), Messages.getWarningIcon());
      if (choice != 0) return false;
      if (!outputDir.mkdirs()) {
        Messages
          .showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.directory.creation.failed", outputDirectory),
                             CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return false;
      }
    }
    else if (!outputDir.isDirectory()) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.output.not.a.directory", outputDirectory),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }
    return true;
  }

  private boolean checkInputDirectory(String inputDirectory) {
    if (inputDirectory == null || inputDirectory.trim().length() == 0) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.input.directory.not.specified"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }

    File outputDir = new File(inputDirectory);
    if (!outputDir.exists()) {
      int choice = Messages
        .showOkCancelDialog(myProject, GroovyDocBundle.message("groovydoc.generate.input.directory.not.exists", inputDirectory),
                            GroovyDocBundle.message("groovydoc.generate.message.title"), Messages.getWarningIcon());
      if (choice != 0) return false;
      if (!outputDir.mkdirs()) {
        Messages
          .showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.input.directory.creation.failed", inputDirectory),
                             CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return false;
      }
    }
    else if (!outputDir.isDirectory()) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.input.not.a.directory", inputDirectory),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }
    return true;
  }
}
