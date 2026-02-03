// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author Aexander Tsarev
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER + 1)
public final class GradleExcludeBuildFilesDataService extends AbstractProjectDataService<ContentRootData, ContentEntry> {

  private static final Logger LOG = Logger.getInstance(GradleExcludeBuildFilesDataService.class);

  public static final String REGISTRY_KEY = "gradle.exclude.build.files.when.in.source.set";

  @Override
  public @NotNull Key<ContentRootData> getTargetDataKey() {
    return ProjectKeys.CONTENT_ROOT;
  }

  @Override
  public void importData(@NotNull Collection<? extends DataNode<ContentRootData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!Registry.get(REGISTRY_KEY).asBoolean()) {
      return;
    }
    if (toImport.isEmpty()) {
      return;
    }

    MultiMap<DataNode<ModuleData>, DataNode<ContentRootData>> byModule = ExternalSystemApiUtil.groupBy(toImport, ModuleData.class);

    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ContentRootData>>> entry : byModule.entrySet()) {
      Module module = entry.getKey().getUserData(AbstractModuleDataService.MODULE_KEY);
      module = module != null ? module : modelsProvider.findIdeModule(entry.getKey().getData());
      if (module == null) {
        LOG.warn(String.format(
          "Can't exclude build files from content root. Reason: target module (%s) is not found at the ide. Content roots: %s",
          entry.getKey(), entry.getValue()
        ));
        continue;
      }
      importData(modelsProvider, entry.getValue(), module);
    }
  }

  private static void importData(@NotNull IdeModifiableModelsProvider modelsProvider,
                                 final @NotNull Collection<? extends DataNode<ContentRootData>> data,
                                 final @NotNull Module module) {
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    for (final DataNode<ContentRootData> node : data) {
      final ContentRootData contentRoot = node.getData();
      final String rootPath = contentRoot.getRootPath();
      final ContentEntry contentEntry = findContentRoot(modifiableRootModel, rootPath);
      if(contentEntry == null) {
        continue;
      }

      final Collection<String> toExclude = new HashSet<>(GradleConstants.KNOWN_GRADLE_FILES.size());
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        final VirtualFile sourceFolderRoot = sourceFolder.getFile();
        if (sourceFolderRoot != null && sourceFolderRoot.isDirectory()) {
          for (String knownGradleFile : GradleConstants.KNOWN_GRADLE_FILES) {
            final VirtualFile buildFile = sourceFolderRoot.findChild(knownGradleFile);
            if (buildFile != null && buildFile.exists()) {
              toExclude.add(knownGradleFile);
            }
          }
        }
      }

      final List<String> existingPatterns = contentEntry.getExcludePatterns();
      for (String toExcludeFile : toExclude) {
        if (!existingPatterns.contains(toExcludeFile)) {
          logDebug("Excluding build file '%s' for module '%s'", toExcludeFile, module.getName());
          contentEntry.addExcludePattern(toExcludeFile);
        }
      }
    }
  }

  private static @Nullable ContentEntry findContentRoot(@NotNull ModifiableRootModel model, @NotNull String path) {
    ContentEntry[] entries = model.getContentEntries();

    for (ContentEntry entry : entries) {
      VirtualFile file = entry.getFile();
      if (file != null && ExternalSystemApiUtil.getLocalFileSystemPath(file).equals(path)) {
        return entry;
      }
    }
    return null;
  }

  private static void logDebug(@NotNull String format, Object... args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format(format, args));
    }
  }
}
