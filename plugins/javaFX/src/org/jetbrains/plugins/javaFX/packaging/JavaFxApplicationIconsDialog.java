package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class JavaFxApplicationIconsDialog extends DialogWrapper {
  private Panel myPanel;
  private final Project myProject;

  public JavaFxApplicationIconsDialog(JComponent parent, JavaFxApplicationIcons icons, Project project) {
    super(parent, true);
    myProject = project;
    setTitle("Choose Application Icons");
    init();

    if (icons != null) {
      JavaFxArtifactPropertiesEditor.setSystemDependentPath(myPanel.myLinuxIconPath, icons.getLinuxIcon());
      JavaFxArtifactPropertiesEditor.setSystemDependentPath(myPanel.myMacIconPath, icons.getMacIcon());
      JavaFxArtifactPropertiesEditor.setSystemDependentPath(myPanel.myWindowsIconPath, icons.getWindowsIcon());
    }

    addBrowseListener(myPanel.myLinuxIconPath, "png", project);
    addBrowseListener(myPanel.myMacIconPath, "icns", project);
    addBrowseListener(myPanel.myWindowsIconPath, "ico", project);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myPanel = new Panel();
    return myPanel.myWholePanel;
  }

  @Override
  protected void doOKAction() {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (!isValidPath(myPanel.myLinuxIconPath, index, "Linux")) return;
    if (!isValidPath(myPanel.myMacIconPath, index, "Mac")) return;
    if (!isValidPath(myPanel.myWindowsIconPath, index, "Windows")) return;

    super.doOKAction();
  }

  @NotNull
  public JavaFxApplicationIcons getIcons() {
    JavaFxApplicationIcons icons = new JavaFxApplicationIcons();
    icons.setLinuxIcon(JavaFxArtifactPropertiesEditor.getSystemIndependentPath(myPanel.myLinuxIconPath));
    icons.setMacIcon(JavaFxArtifactPropertiesEditor.getSystemIndependentPath(myPanel.myMacIconPath));
    icons.setWindowsIcon(JavaFxArtifactPropertiesEditor.getSystemIndependentPath(myPanel.myWindowsIconPath));
    return icons;
  }

  private boolean isValidPath(TextFieldWithBrowseButton withBrowseButton, ProjectFileIndex index, String osName) {
    final String text = withBrowseButton.getText();
    if (StringUtil.isEmptyOrSpaces(text)) return true;
    final VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(text.trim()), false);
    if (virtualFile == null || !virtualFile.exists() || virtualFile.isDirectory()) {
      Messages.showErrorDialog(myPanel.myWholePanel, osName + " icon file should exist");
      return false;
    }
    if (!index.isInContent(virtualFile)) {
      Messages.showErrorDialog(myPanel.myWholePanel, osName + " icon file should be inside the project content");
      return false;
    }
    return true;
  }

  private static void addBrowseListener(TextFieldWithBrowseButton withBrowseButton, String extension, Project project) {
    withBrowseButton.addBrowseFolderListener("Choose Icon File",
                                             "Select icon file (*." + extension + ") for the resulting application", project,
                                             FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                                               .withFileFilter(file -> extension.equalsIgnoreCase(file.getExtension())));
  }

  protected static class Panel {
    JPanel myWholePanel;
    private TextFieldWithBrowseButton myLinuxIconPath;
    private TextFieldWithBrowseButton myMacIconPath;
    private TextFieldWithBrowseButton myWindowsIconPath;
  }
}
