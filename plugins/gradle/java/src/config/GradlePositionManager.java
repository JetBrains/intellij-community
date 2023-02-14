// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.config;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.io.URLUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.resource.DefaultTextFileResourceLoader;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.UriTextResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GradlePositionManager extends ScriptPositionManagerHelper {
  private static final Key<CachedValue<Map<File, String>>> GRADLE_CLASS_NAME = Key.create("GRADLE_CLASS_NAME");

  @Override
  public boolean isAppropriateRuntimeName(@NotNull final String runtimeName) {
    return true;
  }

  @Override
  public boolean isAppropriateScriptFile(@NotNull final GroovyFile scriptFile) {
    return GroovyScriptUtil.isSpecificScriptFile(scriptFile, GradleScriptType.INSTANCE);
  }

  @Override
  @NotNull
  public String getRuntimeScriptName(@NotNull GroovyFile groovyFile) {
    VirtualFile virtualFile = groovyFile.getVirtualFile();
    if (virtualFile == null) return "";

    final Module module = ModuleUtilCore.findModuleForPsiElement(groovyFile);
    if (module == null) {
      return "";
    }

    final File scriptFile = VfsUtilCore.virtualToIoFile(virtualFile);
    final String className = CachedValuesManager.getManager(module.getProject())
                                                .getCachedValue(module, GRADLE_CLASS_NAME, new ScriptSourceMapCalculator(module), false)
                                                .get(scriptFile);
    return className == null ? "" : className;
  }

  @Nullable
  @Override
  public String customizeClassName(@NotNull PsiClass psiClass) {
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof GroovyFile) {
      return getRuntimeScriptName((GroovyFile)file);
    }
    else {
      return null;
    }
  }

  @Override
  public PsiFile getExtraScriptIfNotFound(@NotNull ReferenceType refType,
                                          @NotNull String runtimeName,
                                          @NotNull Project project,
                                          @NotNull GlobalSearchScope scope) {
    String sourceFilePath = getScriptForClassName(refType);
    if (sourceFilePath == null) return null;
    sourceFilePath = getLocalFilePath(project, sourceFilePath);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(sourceFilePath));
    if (virtualFile == null) return null;

    return PsiManager.getInstance(project).findFile(virtualFile);
  }

  @Override
  public Collection<? extends FileType> getAcceptedFileTypes() {
    return Collections.singleton(GradleFileType.INSTANCE);
  }

  private static String getLocalFilePath(@NotNull Project project, @NotNull String sourceFilePath) {
    // TODO add the support for other run targets mappings
    String projectBasePath = project.getBasePath();
    if (projectBasePath != null && WslPath.isWslUncPath(projectBasePath)) {
      WSLDistribution wslDistribution = WslPath.getDistributionByWindowsUncPath(projectBasePath);
      if (wslDistribution != null) {
        sourceFilePath = wslDistribution.getWindowsPath(sourceFilePath);
      }
    }
    return sourceFilePath;
  }

  @Nullable
  private static String getScriptForClassName(@NotNull ReferenceType refType) {
    try {
      final List<String> data = refType.sourcePaths(null);
      if (!data.isEmpty()) {
        return data.get(0);
      }
    }
    catch (AbsentInformationException ignored) {
    }
    return null;
  }

  private static class ScriptSourceMapCalculator implements CachedValueProvider<Map<File, String>> {
    private final Module myModule;

    ScriptSourceMapCalculator(Module module) {
      myModule = module;
    }

    @Override
    public Result<Map<File, String>> compute() {
      final Map<File, String> result = ConcurrentFactoryMap.createMap(ScriptSourceMapCalculator::calcClassName);
      return Result.create(result, ProjectRootManager.getInstance(myModule.getProject()));
    }

    @Nullable
    private static String calcClassName(File scriptFile) {
      TextResource resource = getResource(scriptFile);
      return new TextResourceScriptSource(resource).getClassName();
    }

    private static TextResource getResource(File scriptFile) {
      TextResource resource = null;
      // TODO add the support for other run targets mappings
      if (WslPath.isWslUncPath(scriptFile.getPath())) {
        resource = getWslUriResource(scriptFile);
      }
      if (resource == null) {
        resource = new DefaultTextFileResourceLoader(new IdentityFileResolver()).loadFile("script", scriptFile);
      }
      return resource;
    }

    @Nullable
    private static TextResource getWslUriResource(@NotNull File scriptFile) {
      WSLDistribution wslDistribution = WslPath.getDistributionByWindowsUncPath(scriptFile.getPath());
      if (wslDistribution == null) return null;
      String wslPath = wslDistribution.getWslPath(scriptFile.getPath());
      if (wslPath == null) return null;
      return new UriTextResource("script", pathToUri(wslPath), new IdentityFileResolver());
    }

    // version of File(path).toURI() w/o using system-dependent java.io.File
    private static @Nullable URI pathToUri(@NotNull String path) {
      try {
        String p = slashify(path);
        return new URI(URLUtil.FILE_PROTOCOL, null, p.startsWith("//") ? ("//" + p) : p, null);
      }
      catch (URISyntaxException ignore) {
      }
      return null;
    }

    // java.io.File#slashify
    private static String slashify(String path) {
      String name = FileUtil.toSystemIndependentName(path);
      return name.startsWith("/") ? name : "/" + name;
    }
  }
}
