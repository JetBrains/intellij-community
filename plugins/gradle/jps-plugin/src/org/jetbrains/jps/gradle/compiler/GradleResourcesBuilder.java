// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.compiler;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.gradle.GradleJpsBundle;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.gradle.model.impl.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public class GradleResourcesBuilder extends TargetBuilder<GradleResourceRootDescriptor, GradleResourcesTarget> {
  private static final Logger LOG = Logger.getInstance(GradleResourcesBuilder.class);

  public GradleResourcesBuilder() {
    super(Arrays.asList(GradleResourcesTargetType.PRODUCTION, GradleResourcesTargetType.TEST));
  }

  @Override
  public void build(@NotNull final GradleResourcesTarget target,
                    @NotNull final DirtyFilesHolder<GradleResourceRootDescriptor, GradleResourcesTarget> holder,
                    @NotNull final BuildOutputConsumer outputConsumer,
                    @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    if (!JavaBuilder.IS_ENABLED.get(context, Boolean.TRUE)) {
      return;
    }

    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    final GradleProjectConfiguration projectConfig = JpsGradleExtensionService.getInstance().getGradleProjectConfiguration(dataPaths);
    final GradleModuleResourceConfiguration config = target.getModuleResourcesConfiguration(dataPaths);
    if (config == null) return;

    final Map<GradleResourceRootDescriptor, List<File>> files = new HashMap<>();

    holder.processDirtyFiles(new FileProcessor<GradleResourceRootDescriptor, GradleResourcesTarget>() {

      @Override
      public boolean apply(GradleResourcesTarget t, File file, GradleResourceRootDescriptor rd) throws IOException {
        assert target == t;

        List<File> fileList = files.get(rd);
        if (fileList == null) {
          fileList = new ArrayList<>();
          files.put(rd, fileList);
        }

        fileList.add(file);
        return true;
      }
    });

    GradleResourceRootDescriptor[] roots = files.keySet().toArray(new GradleResourceRootDescriptor[0]);
    Arrays.sort(roots, (r1, r2) -> {
      int res = r1.getIndexInPom() - r2.getIndexInPom();
      if (r1.isOverwrite()) {
        assert r2.isOverwrite();
        return res;
      }

      if (r1.getConfiguration().isFiltered && !r2.getConfiguration().isFiltered) return 1;
      if (!r1.getConfiguration().isFiltered && r2.getConfiguration().isFiltered) return -1;

      if (!r1.getConfiguration().isFiltered) {
        res = -res;
      }

      return res;
    });

    ResourceFileProcessor fileProcessor;
    try {
      fileProcessor = new GradleResourceFileProcessor(projectConfig, target.getModule().getProject(), config);
    }
    catch (Throwable t) {
      LOG.warn("Can not create resource file processor", t);
      fileProcessor = new ResourceFileProcessor() {
        @Override
        public void copyFile(File file,
                             Ref<File> targetFileRef,
                             ResourceRootConfiguration rootConfiguration,
                             CompileContext context,
                             FileFilter filteringFilter) throws IOException {
          FSOperations.copy(file, targetFileRef.get());
        }
      };
    }

    for (GradleResourceRootDescriptor rd : roots) {
      for (File file : files.get(rd)) {

        String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) continue;

        final File outputDir =
          GradleResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration(), config.outputDirectory);
        if (outputDir == null) continue;

        context.processMessage(new ProgressMessage(GradleJpsBundle.message("copying.resources.0", target.getModule().getName())));

        final Ref<File> fileRef = Ref.create(new File(outputDir, relPath));
        fileProcessor.copyFile(file, fileRef, rd.getConfiguration(), context, FileFilters.EVERYTHING);
        outputConsumer.registerOutputFile(fileRef.get(), Collections.singleton(file.getPath()));

        if (context.getCancelStatus().isCanceled()) return;
      }
    }

    context.checkCanceled();
    context.processMessage(new ProgressMessage(""));
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return GradleJpsBundle.message("gradle.resources.compiler");
  }
}
