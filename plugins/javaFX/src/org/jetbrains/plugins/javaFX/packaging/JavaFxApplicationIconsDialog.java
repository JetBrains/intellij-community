// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;
import java.io.File;

public final class JavaFxApplicationIconsDialog extends DialogWrapper {
  private Panel myPanel;
  private final Project myProject;

  public JavaFxApplicationIconsDialog(JComponent parent, JavaFxApplicationIcons icons, Project project) {
    super(parent, true);
    myProject = project;
    setTitle(JavaFXBundle.message("javafx.application.icons.choose.icons"));
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

  @Override
  protected @Nullable JComponent createCenterPanel() {
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

  public @NotNull JavaFxApplicationIcons getIcons() {
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
      Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.application.icons.icon.file.should.exist", osName));
      return false;
    }
    if (!index.isInContent(virtualFile)) {
      Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.application.icons.file.should.be.inside.project.content", osName));
      return false;
    }
    return true;
  }

  private static void addBrowseListener(TextFieldWithBrowseButton withBrowseButton, String extension, Project project) {
    withBrowseButton.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileDescriptor(extension)
      .withTitle(JavaFXBundle.message("javafx.application.icons.select.icon.file.title"))
      .withDescription(JavaFXBundle.message("javafx.application.icons.select.icon.file.description", extension)));
  }

  protected static final class Panel {
    JPanel myWholePanel;
    private TextFieldWithBrowseButton myLinuxIconPath;
    private TextFieldWithBrowseButton myMacIconPath;
    private TextFieldWithBrowseButton myWindowsIconPath;
  }
}
