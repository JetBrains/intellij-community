package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.gradle.SourceType;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collections;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
public class GradleContentRootManager {

  public void importContentRoots(@NotNull GradleContentRoot contentRoot, @NotNull Module module) {
    importContentRoots(Collections.singleton(contentRoot), module);
  }
  
  @SuppressWarnings("MethodMayBeStatic")
  public void importContentRoots(@NotNull final Iterable<GradleContentRoot> contentRoots, @NotNull final Module module) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        doImportContentRoots(contentRoots, module);
      }
    });
  }

  private static void doImportContentRoots(@NotNull final Iterable<GradleContentRoot> contentRoots, @NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel model = moduleRootManager.getModifiableModel();
        GradleUtil.executeProjectChangeAction(module.getProject(), contentRoots, new Runnable() {
          @Override
          public void run() {
            try {
              for (GradleContentRoot contentRoot : contentRoots) {
                ContentEntry contentEntry = model.addContentEntry(toVfsUrl(contentRoot.getRootPath()));
                for (String path : contentRoot.getPaths(SourceType.SOURCE)) {
                  contentEntry.addSourceFolder(toVfsUrl(path), false);
                }
                for (String path : contentRoot.getPaths(SourceType.TEST)) {
                  contentEntry.addSourceFolder(toVfsUrl(path), true);
                }
                for (String path : contentRoot.getPaths(SourceType.EXCLUDED)) {
                  contentEntry.addExcludeFolder(toVfsUrl(path));
                }
              }
            }
            finally {
              model.commit();
            } 
          }
        });
      }
    });
  }
  
  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
  
  public void removeContentRoots(@NotNull final Iterable<ModuleAwareContentRoot> contentRoots) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (final ModuleAwareContentRoot contentRoot : contentRoots) {
          GradleUtil.executeProjectChangeAction(contentRoot.getModule().getProject(), contentRoot, new Runnable() {
            @Override
            public void run() {
              final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(contentRoot.getModule());
              ModifiableRootModel model = moduleRootManager.getModifiableModel();
              try {
                model.removeContentEntry(contentRoot);
              }
              finally {
                model.commit();
              } 
            }
          });
        }
      }
    });
  }
}