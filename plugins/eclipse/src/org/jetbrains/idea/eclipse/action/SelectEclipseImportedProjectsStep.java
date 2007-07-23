/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseProjectModel;
import org.jetbrains.idea.eclipse.util.PathUtil;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

class SelectEclipseImportedProjectsStep extends SelectImportedProjectsStep<EclipseProjectModel> {
  private static final Icon ICON_CONFLICT = IconLoader.getIcon("/actions/cancel.png");

  Set<String> duplicateNames;

  public SelectEclipseImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<EclipseProjectModel>() {
      public void elementMarkChanged(final EclipseProjectModel element, final boolean isMarked) {
        duplicateNames = null;
        fileChooser.repaint();
      }
    });
  }

  private boolean isInConflict(final EclipseProjectModel item) {
    calcDuplicates();
    return fileChooser.getMarkedElements().contains(item) && duplicateNames.contains(item.getName());
  }

  private void calcDuplicates() {
    if (duplicateNames == null) {
      duplicateNames = new HashSet<String>();
      Set<String> usedNames = new HashSet<String>();
      for (EclipseProjectModel model : fileChooser.getMarkedElements()) {
        if (!usedNames.add(model.getName())) {
          duplicateNames.add(model.getName());
        }
      }
    }
  }

  protected String getElementText(final EclipseProjectModel item) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(item.getName());
    String relPath = PathUtil.getRelative(((EclipseImportBuilder)getBuilder()).getParameters().workspace.getRoot(), item.getRoot());
    if (!relPath.equals(".") && !relPath.equals(item.getName())) {
      stringBuilder.append(" (").append(relPath).append(")");
    }
    return stringBuilder.toString();
  }

  @Nullable
  protected Icon getElementIcon(final EclipseProjectModel item) {
    return isInConflict(item) ? ICON_CONFLICT : null;
  }

  public boolean validate() throws ConfigurationException {
    calcDuplicates();
    if (duplicateNames.isEmpty()) {
      throw new ConfigurationException("Duplicated names found:" + StringUtil.join(duplicateNames.toArray(new String[duplicateNames.size()]), ","), "Unable to proceed");
    }
    return super.validate();
  }
}