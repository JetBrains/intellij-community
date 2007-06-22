package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ExportEclipseProjectsDialog extends DialogWrapper {
  private JPanel contentPane;
  private ElementsChooser<Module> moduleChooser;
  private JCheckBox linkCheckBox;

  public ExportEclipseProjectsDialog(final Project project, List<Module> modules) {
    super(project, false);
    moduleChooser.setElements(modules, true);
    setTitle(EclipseBundle.message("eclipse.export.dialog.title"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  private void createUIComponents() {
    moduleChooser = new ElementsChooser<Module>(true) {
      protected String getItemText(final Module module) {
        return module.getName();
      }
    };
  }

  public boolean isLink() {
    return linkCheckBox.isSelected();
  }

  public List<Module> getSelectedModules() {
    return moduleChooser.getMarkedElements();
  }
}
