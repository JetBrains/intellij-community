// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.builder.CppModelBuilder;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.*;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl.*;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
@Order(ExternalSystemConstants.UNORDERED)
public class GradleNativeProjectResolver extends AbstractProjectResolverExtension {
  @NotNull public static final Key<CppProject> CPP_PROJECT = Key.create(CppProject.class, ProjectKeys.MODULE.getProcessingWeight() + 1);

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    CppProject cppProject = resolverCtx.getExtraProject(gradleModule, CppProject.class);
    if (cppProject != null) {
      // store a local process copy of the object to get rid of proxy types for further serialization
      ideModule.createChild(CPP_PROJECT, copy(cppProject));

      Set<SourceFolder> sourceFolders = cppProject.getSourceFolders();
      for (SourceFolder folder : sourceFolders) {
        File baseDir = folder.getBaseDir();
        ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, baseDir.getAbsolutePath());
        ideContentRoot.storePath(ExternalSystemSourceType.SOURCE, baseDir.getAbsolutePath());
        ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
      }
    }

    nextResolver.populateModuleContentRoots(gradleModule, ideModule);
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    return Collections.singleton(CppProject.class);
  }

  @NotNull
  @Override
  public Set<Class> getToolingExtensionsClasses() {
    return ContainerUtil.set(
      // native-gradle-tooling jar
      CppModelBuilder.class
    );
  }

  @NotNull
  private static CppProject copy(@NotNull CppProject cppProject) {
    CppProjectImpl copy = new CppProjectImpl();
    for (CppBinary binary : cppProject.getBinaries()) {
      copy.addBinary(copy(binary));
    }
    for (SourceFolder sourceFolder : cppProject.getSourceFolders()) {
      copy.addSourceFolder(copy(sourceFolder));
    }
    return copy;
  }

  private static SourceFolder copy(SourceFolder sourceFolder) {
    FilePatternSet patterns = sourceFolder.getPatterns();
    return new SourceFolderImpl(sourceFolder.getBaseDir(), new FilePatternSetImpl(patterns.getIncludes(),
                                                                                  patterns.getExcludes()));
  }

  @NotNull
  private static CppBinary copy(@NotNull CppBinary binary) {
    return new CppBinaryImpl(binary.getBaseName(), binary.getVariantName(), binary.getSources(),
                             copy(binary.getCompilerDetails()), copy(binary.getLinkerDetails()), binary.getTargetType());
  }

  private static LinkerDetails copy(LinkerDetails details) {
    return new LinkerDetailsImpl(details.getLinkTaskName(), details.getOutputFile());
  }

  private static CompilerDetails copy(CompilerDetails details) {
    return new CompilerDetailsImpl(details.getCompileTaskName(), details.getExecutable(), details.getWorkingDir(), details.getArgs(),
                                   details.getIncludePath(), details.getSystemIncludes());
  }
}
