// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.util.PathUtil;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

class SelectEclipseImportedProjectsStep extends SelectImportedProjectsStep<String> {

  Set<@NlsSafe String> duplicateNames;

  SelectEclipseImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<>() {
      @Override
      public void elementMarkChanged(final String element, final boolean isMarked) {
        duplicateNames = null;
        fileChooser.repaint();
      }
    });
  }

  private boolean isInConflict(final String item) {
    calcDuplicates();
    return fileChooser.getMarkedElements().contains(item) && duplicateNames.contains(EclipseProjectFinder.findProjectName(item));
  }

  private void calcDuplicates() {
    if (duplicateNames == null) {
      duplicateNames = new HashSet<>();
      Set<String> usedNames = new HashSet<>();
      for (String model : fileChooser.getMarkedElements()) {
        final String projectName = EclipseProjectFinder.findProjectName(model);
        if (!usedNames.add(projectName)) {
          duplicateNames.add(projectName);
        }
      }
    }
  }

  @Override
  protected String getElementText(final String item) {
    StringBuilder stringBuilder = new StringBuilder();
    final String projectName = EclipseProjectFinder.findProjectName(item);
    stringBuilder.append(projectName);
    String relPath = PathUtil.getRelative(((EclipseImportBuilder)getBuilder()).getParameters().root, item);
    if (!relPath.equals(".") && !relPath.equals(projectName)) {
      stringBuilder.append(" (").append(relPath).append(")");
    }
    return stringBuilder.toString();
  }

  @Override
  @Nullable
  protected Icon getElementIcon(final String item) {
    return isInConflict(item) ? AllIcons.Actions.Cancel : null;
  }

  @Override
  public void updateStep() {
    super.updateStep();
    duplicateNames = null;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    calcDuplicates();
    if (!duplicateNames.isEmpty()) {
      throw new ConfigurationException(
        EclipseBundle.message("duplicate.names.found.import.error.message", StringUtil.join(duplicateNames, ",")),
        EclipseBundle.message("unable.to.proceed.import.title"));
    }
    return super.validate();
  }

  @Override
  public String getName() {
    return EclipseBundle.message("eclipse.projects.to.import.selection.step.name");
  }

  @Override
  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import.eclipse.page2";
  }
}
