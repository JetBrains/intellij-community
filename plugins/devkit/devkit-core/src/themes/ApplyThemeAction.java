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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

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
      UITheme theme = UITheme.loadFromJson(json.getInputStream(), "Temp theme", null, createIconsMapper(json, project));
      String pathToScheme = theme.getEditorScheme();
      VirtualFile editorScheme = null;
      if (pathToScheme != null) {
        editorScheme = findThemeFile(json, project, pathToScheme);
      }

      patchBackgroundImagePath(json, project, theme.getBackground());
      patchBackgroundImagePath(json, project, theme.getEmptyFrameBackground());

      LafManager.getInstance().setCurrentLookAndFeel(new TempUIThemeBasedLookAndFeelInfo(theme, editorScheme));
      IconLoader.clearCache();
      LafManager.getInstance().updateUI();
      return true;
    }
    catch (IOException ignore) {}
    return false;
  }

  private static void patchBackgroundImagePath(@NotNull VirtualFile json, Project project, Map<String, Object> background) {
    if (background != null) {
      VirtualFile pathToBg = findThemeFile(json, project, background.get("image").toString());
      if (pathToBg != null) {
        background.put("image", pathToBg.getPath());
      }
    }
  }

  @Nullable
  private static VirtualFile findThemeFile(@NotNull VirtualFile json, Project project, String pathToFile) {
    Module module = ModuleUtilCore.findModuleForFile(json, project);
    if (module != null) {
      for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(false)) {
        Path path = Paths.get(root.getPath(), pathToFile);
        if (path.toFile().exists()) {
          return VfsUtil.findFile(path, true);
        }
      }
    }
    return null;
  }

  private static Function<String, String> createIconsMapper(VirtualFile json, Project project) {
    Module module = ModuleUtilCore.findModuleForFile(json, project);
    if (module != null) {
      return s -> findAbsoluteFilePathByRelativePath(module, s, s);
    }
    return s -> s;
  }

  @Nullable
  private static String findAbsoluteFilePathByRelativePath(Module module, String relativePath, String defaultResult) {
    String filename = new File(relativePath).getName();
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
    Collection<VirtualFile> filesByName = FilenameIndex.getVirtualFilesByName(module.getProject(), filename, moduleScope);
    for (VirtualFile file : filesByName) {
      String path = file.getPath();
      if (path.endsWith(relativePath) || path.endsWith(relativePath.replaceAll("/", "\\"))) {
        return "file:/" + path;
      }
    }
    return defaultResult;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (LafManager.getInstance().getCurrentLookAndFeel() instanceof TempUIThemeBasedLookAndFeelInfo) {
      e.getPresentation().setIcon(AllIcons.Actions.Rerun);
    }
  }
}
