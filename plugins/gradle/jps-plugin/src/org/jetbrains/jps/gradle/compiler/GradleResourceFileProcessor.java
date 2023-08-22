// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.compiler;

import com.intellij.openapi.util.Ref;
import org.apache.tools.ant.util.ReaderInputStream;
import org.jetbrains.jps.gradle.GradleJpsBundle;
import org.jetbrains.jps.gradle.model.impl.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class GradleResourceFileProcessor implements ResourceFileProcessor {
  private static final int FILTERING_SIZE_LIMIT = 10 * 1024 * 1024 /*10 mb*/;
  protected final JpsEncodingProjectConfiguration myEncodingConfig;
  protected final GradleProjectConfiguration myProjectConfig;
  protected final GradleModuleResourceConfiguration myModuleConfiguration;

  public GradleResourceFileProcessor(GradleProjectConfiguration projectConfiguration, JpsProject project,
                                     GradleModuleResourceConfiguration moduleConfiguration) {
    myProjectConfig = projectConfiguration;
    myEncodingConfig = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(project);
    myModuleConfiguration = moduleConfiguration;
  }

  @Override
  public void copyFile(File file, Ref<File> targetFileRef, ResourceRootConfiguration rootConfiguration, CompileContext context,
                       FileFilter filteringFilter) throws IOException {
    boolean shouldFilter = rootConfiguration.isFiltered && !rootConfiguration.filters.isEmpty() && filteringFilter.accept(file);
    if (shouldFilter && file.length() > FILTERING_SIZE_LIMIT) {
      context.processMessage(new CompilerMessage(
        GradleJpsBundle.message("gradle.resources.compiler"), BuildMessage.Kind.WARNING,
        GradleJpsBundle.message("file.is.too.big.to.be.filtered"), file.getPath())
      );
      shouldFilter = false;
    }
    if (shouldFilter) {
      copyWithFiltering(file, targetFileRef, rootConfiguration.filters, context);
    }
    else {
      FSOperations.copy(file, targetFileRef.get());
    }
  }

  private static void copyWithFiltering(File file, Ref<File> outputFileRef, List<ResourceRootFilter> filters, CompileContext context) throws IOException {
    try (InputStream inputStream = transform(file, filters, outputFileRef, context)) {
      Path target = outputFileRef.get().toPath();
      Files.createDirectories(target.getParent());
      Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static InputStream transform(File file,
                                       List<ResourceRootFilter> filters,
                                       Ref<File> outputFileRef,
                                       CompileContext context) throws FileNotFoundException {
    Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
    Reader transformer = new ChainingFilterTransformer(context, filters, outputFileRef).transform(reader);
    return new ReaderInputStream(transformer);
  }
}