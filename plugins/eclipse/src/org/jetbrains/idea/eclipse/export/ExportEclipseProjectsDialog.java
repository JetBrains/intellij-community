package org.jetbrains.idea.eclipse.export;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class ExportEclipseProjectsDialog extends DialogWrapper {
  private JPanel contentPane;
  private ElementsChooser<Module> moduleChooser;
  private JCheckBox linkCheckBox;
  private TextFieldWithBrowseButton myUserLibrariesTF;

  public ExportEclipseProjectsDialog(final Project project, List<Module> modules) {
    super(project, false);
    moduleChooser.setElements(modules, true);
    setTitle(EclipseBundle.message("eclipse.export.dialog.title"));
    init();
    myUserLibrariesTF.setText(project.getBaseDir().getPath() + File.separator + project.getName() + ".userlibraries");
    myUserLibrariesTF.addBrowseFolderListener("Locate .userlibraries", "Locate .userlibraries file where project libraries would be exported", project, FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
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

  public File getUserLibrariesFile() {
    return new File(myUserLibrariesTF.getText());
  }
}
