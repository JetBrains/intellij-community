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

/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.SelectImportedProjectsStep;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.util.PathUtil;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

class SelectEclipseImportedProjectsStep extends SelectImportedProjectsStep<String> {

  Set<String> duplicateNames;

  public SelectEclipseImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<String>() {
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

  @Nullable
  protected Icon getElementIcon(final String item) {
    return isInConflict(item) ? AllIcons.Actions.Cancel : null;
  }

  public void updateStep() {
    super.updateStep();
    duplicateNames = null;
  }

  public boolean validate() throws ConfigurationException {
    calcDuplicates();
    if (!duplicateNames.isEmpty()) {
      throw new ConfigurationException("Duplicate names found:" + StringUtil.join(ArrayUtil.toStringArray(duplicateNames), ","), "Unable to proceed");
    }
    return super.validate();
  }

  @Override
  public String getName() {
    return "Eclipse Projects to Import";
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import.eclipse.page2";
  }
}
