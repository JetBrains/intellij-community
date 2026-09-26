// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.TempUIThemeLookAndFeelInfo;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class ApplyThemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    applyTempTheme(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (LafManager.getInstance().getCurrentUIThemeLookAndFeel() instanceof TempUIThemeLookAndFeelInfo) {
      e.getPresentation().setIcon(AllIcons.Actions.Rerun);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Contract("null -> false")
  private static boolean isThemeFile(@Nullable VirtualFile file) {
    return file != null && StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), UITheme.FILE_EXT_ENDING);
  }

  public static boolean applyTempTheme(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (!isThemeFile(file)) {
      file = null;
    }

    if (file == null) {
      for (VirtualFile virtualFile : FileEditorManager.getInstance(project).getOpenFiles()) {
        if (isThemeFile(virtualFile)) {
          file = virtualFile;
          break;
        }
      }
    }

    if (file == null) {
      file = ContainerUtil.getFirstItem(FilenameIndex.getAllFilesByExt(project, "theme.json", GlobalSearchScope.projectScope(project)));
    }
    if (file != null && isThemeFile(file)) {
      return applyTempTheme(file, project);
    }
    return false;
  }

  private static boolean applyTempTheme(@NotNull VirtualFile json, @NotNull Project project) {
    try {
      FileDocumentManager.getInstance().saveAllDocuments();

      Module module = ModuleUtilCore.findModuleForFile(json, project);
      UITheme theme = TempUIThemeLookAndFeelInfo.loadTempTheme(json.getInputStream(), new IconPathPatcher() {
        @Override
        public @NotNull String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
          String result = module == null ? null : findAbsoluteFilePathByRelativePath(module, path);
          return result == null ? path : result;
        }
      });

      if (module != null) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

        patchBackgroundImagePath(moduleRootManager, theme.getBackground());
        patchBackgroundImagePath(moduleRootManager, theme.getEmptyFrameBackground());
      }

      LafManager lafManager = LafManager.getInstance();
      lafManager.setCurrentUIThemeLookAndFeel(new TempUIThemeLookAndFeelInfo(theme, lafManager.getCurrentUIThemeLookAndFeel()));
      IconLoader.clearCache();
      lafManager.updateUI();
      return true;
    }
    catch (IOException ignore) {
    }
    return false;
  }

  private static void patchBackgroundImagePath(@NotNull ModuleRootManager moduleRootManager,
                                               @NotNull Map<String, Object> background) {
    if (!background.isEmpty()) {
      VirtualFile pathToBg = findThemeFile(moduleRootManager, background.get("image").toString());
      if (pathToBg != null) {
        background.put("image", pathToBg.getPath());
      }
    }
  }

  private static @Nullable VirtualFile findThemeFile(@NotNull ModuleRootManager moduleRootManager,
                                                     @Nullable String pathToFile) {
    if (pathToFile != null) {
      for (VirtualFile root : moduleRootManager.getSourceRoots(false)) {
        Path path = Paths.get(root.getPath(), pathToFile);
        if (path.toFile().exists()) {
          return VfsUtil.findFile(path, true);
        }
      }
    }

    return null;
  }

  private static @Nullable String findAbsoluteFilePathByRelativePath(@NotNull Module module,
                                                                     @NotNull String relativePath) {
    String filename = new File(relativePath).getName();
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
    Collection<VirtualFile> filesByName = ReadAction.compute(() -> FilenameIndex.getVirtualFilesByName(filename, moduleScope));
    for (VirtualFile file : filesByName) {
      String path = file.getPath();
      if (path.endsWith(relativePath) || path.endsWith(relativePath.replaceAll("/", "\\"))) {
        return "file://" + path; // NON-NLS
      }
    }
    return null;
  }
}
