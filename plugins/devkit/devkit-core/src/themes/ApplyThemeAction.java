// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Konstantin Bulenkov
 */
public class ApplyThemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null || !UITheme.isThemeFile(file)) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getSelectedEditors()) {
        if (UITheme.isThemeFile(fileEditor.getFile())) {
          file = fileEditor.getFile();
          break;
        }
      }
    }

    if (file != null && UITheme.isThemeFile(file)) {
      applyTempTheme(file, project);
    }
  }

  private static void applyTempTheme(@NotNull VirtualFile json, Project project) {
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      UITheme theme = UITheme.loadFromJson(json.getInputStream(), "Temp theme", null);
      String pathToScheme = theme.getEditorScheme();
      VirtualFile editorScheme = null;
      if (pathToScheme != null) {
        Module module = ModuleUtilCore.findModuleForFile(json, project);
        if (module != null) {
          for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(false)) {
            Path path = Paths.get(root.getPath(), pathToScheme);
            if (path.toFile().exists()) {
              editorScheme = VfsUtil.findFile(path, true);
            }
          }
        }
      }
      LafManager.getInstance().setCurrentLookAndFeel(new TempUIThemeBasedLookAndFeelInfo(theme, createTempEditorSchemeFile(editorScheme)));
      LafManager.getInstance().updateUI();
    }
    catch (IOException ignore) {}
  }

  private static Path createTempEditorSchemeFile(VirtualFile editorSchemeFile) {
    if (editorSchemeFile == null) return null;
    try {
      Element root = JDOMUtil.load(editorSchemeFile.getInputStream());
      Attribute name = root.getAttribute("name");

      if (name != null && StringUtil.isNotEmpty(name.getValue())) {
        String newName = name.getValue() + System.currentTimeMillis();
        File file = FileUtil.createTempFile(newName, ".xml", true);
        root.setAttribute("name", newName);
        JDOMUtil.write(root, file);
        return file.toPath();
      }
    }
    catch (Exception ignore) {}

    return null;
  }
}
