/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author John Murph
 */
public class GradlePositionManager extends ScriptPositionManagerHelper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.gradle.GradlePositionManager");
  private static final Pattern GRADLE_CLASS_PATTERN = Pattern.compile(".*_gradle_.*");
  private static final Key<CachedValue<ClassLoader>> GRADLE_CLASS_LOADER = Key.create("GRADLE_CLASS_LOADER");
  private static final Key<CachedValue<FactoryMap<File, String>>> GRADLE_CLASS_NAME = Key.create("GRADLE_CLASS_NAME");

  public boolean isAppropriateRuntimeName(@NotNull final String runtimeName) {
    return GRADLE_CLASS_PATTERN.matcher(runtimeName).matches();
  }

  public boolean isAppropriateScriptFile(@NotNull final PsiFile scriptFile) {
    return GroovyScriptType.getScriptType((GroovyFile)scriptFile) instanceof GradleScriptType;
  }

  @NotNull
  public String getRuntimeScriptName(@NotNull final String originalName, GroovyFile groovyFile) {
    VirtualFile virtualFile = groovyFile.getVirtualFile();
    if (virtualFile == null) return "";

    final Module module = ModuleUtil.findModuleForPsiElement(groovyFile);
    if (module == null) {
      return "";
    }

    final File scriptFile = VfsUtil.virtualToIoFile(virtualFile);
    final String className = CachedValuesManager.getManager(module.getProject())
      .getCachedValue(module, GRADLE_CLASS_NAME, new ScriptSourceMapCalculator(module), false).get(scriptFile);
    return className == null ? "" : className;
  }

  public PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull String runtimeName, Project project) {
    String sourceFilePath = getScriptForClassName(refType);
    if (sourceFilePath == null) return null;

    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(sourceFilePath));
    if (virtualFile == null) return null;

    return PsiManager.getInstance(project).findFile(virtualFile);
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

  @Nullable
  private static ClassLoader getGradleClassLoader(@NotNull final Module module) {
    final Project project = module.getProject();
    return CachedValuesManager.getManager(project).getCachedValue(module, GRADLE_CLASS_LOADER, new CachedValueProvider<ClassLoader>() {
        public Result<ClassLoader> compute() {
          return Result.create(createGradleClassLoader(module), ProjectRootManager.getInstance(project));
        }
      }, false);
  }

  @Nullable
  private static ClassLoader createGradleClassLoader(@NotNull Module module) {
    final VirtualFile sdkHome = GradleLibraryManager.getSdkHome(module, module.getProject());
    if (sdkHome == null) {
      return null;
    }

    List<URL> urls = new ArrayList<URL>();
    final VirtualFile libDir = sdkHome.findChild("lib");
    assert libDir != null;
    for (final VirtualFile child : libDir.getChildren()) {
      if ("jar".equals(child.getExtension())) {
        urls.add(VfsUtil.convertToURL(child.getUrl()));
      }
    }

    return new UrlClassLoader(urls, null);
  }

  private static class ScriptSourceMapCalculator implements CachedValueProvider<FactoryMap<File, String>> {
    private final Module myModule;

    public ScriptSourceMapCalculator(Module module) {
      myModule = module;
    }

    public Result<FactoryMap<File, String>> compute() {
      final FactoryMap<File, String> result = new ConcurrentFactoryMap<File, String>() {
        @Override
        protected String create(File scriptFile) {
          return calcClassName(scriptFile);
        }
      };
      return Result.create(result, ProjectRootManager.getInstance(myModule.getProject()));
    }

    @Nullable
    private String calcClassName(File scriptFile) {
      final ClassLoader loader = getGradleClassLoader(myModule);
      if (loader != null) {
        Class<?> fileScriptSource;
        try {
          fileScriptSource = Class.forName("org.gradle.groovy.scripts.UriScriptSource", true, loader);
        }
        catch (ClassNotFoundException e) {
          try {
            fileScriptSource = Class.forName("org.gradle.groovy.scripts.FileScriptSource", true, loader); //before 0.9
          }
          catch (ClassNotFoundException e1) {
            return null;
          }
        }

        try {
          final Object source = fileScriptSource.getConstructor(String.class, File.class).newInstance("script", scriptFile);
          return (String)fileScriptSource.getMethod("getClassName").invoke(source);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      return null;
    }
  }
}