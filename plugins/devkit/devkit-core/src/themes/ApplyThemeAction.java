// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Konstantin Bulenkov
 */
public class ApplyThemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    applyTempTheme(e);
  }

  public static boolean applyTempTheme(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (!UITheme.isThemeFile(file)) {
      file = null;
    }

    if (file == null) {
      for (VirtualFile virtualFile : FileEditorManager.getInstance(project).getOpenFiles()) {
        if (UITheme.isThemeFile(virtualFile)) {
          file = virtualFile;
          break;
        }
      }
    }

    if (file == null) {
        file = ContainerUtil.getFirstItem(
          FilenameIndex.getAllFilesByExt(project, "theme.json", GlobalSearchScope.projectScope(project))
        );
    }
    if (file != null && UITheme.isThemeFile(file)) {
      return applyTempTheme(file, project);
    }
    return false;
  }

  private static boolean applyTempTheme(@NotNull VirtualFile json, Project project) {
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

      LafManager.getInstance().setCurrentLookAndFeel(new TempUIThemeBasedLookAndFeelInfo(theme, editorScheme));
      LafManager.getInstance().updateUI();
      return true;
    }
    catch (IOException ignore) {}
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (LafManager.getInstance().getCurrentLookAndFeel() instanceof TempUIThemeBasedLookAndFeelInfo) {
      e.getPresentation().setIcon(AllIcons.Actions.Rerun);
    }
  }
}
