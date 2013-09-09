/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class GroovyUtils {
  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";

  public static File[] getFilesInDirectoryByPattern(String dirPath, final String patternString) {
    final Pattern pattern = Pattern.compile(patternString);
    return getFilesInDirectoryByPattern(dirPath, pattern);
  }

  public static File[] getFilesInDirectoryByPattern(String dirPath, final Pattern pattern) {
    File distDir = new File(dirPath);
    File[] files = distDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return pattern.matcher(name).matches();
      }
    });
    return files != null ? files : new File[0];
  }

  public static <E> List<E> flatten(Collection<? extends Collection<E>> collections) {
    List<E> result = new ArrayList<E>();
    for (Collection<E> list : collections) {
      result.addAll(list);
    }

    return result;
  }

  public static boolean isSuitableModule(Module module) {
    if (module == null) return false;
    return isAcceptableModuleType(ModuleType.get(module));
  }

  public static boolean isAcceptableModuleType(ModuleType type) {
    return type instanceof JavaModuleType || PLUGIN_MODULE_ID.equals(type.getId()) || "ANDROID_MODULE".equals(type.getId());
  }

  @Nullable
  public static GrTypeDefinition getPublicClass(@Nullable VirtualFile virtualFile, PsiManager manager) {
    if (virtualFile == null) return null;

    PsiFile psiFile = manager.findFile(virtualFile);
    if ((psiFile instanceof GroovyFile)) {
      return getClassDefinition((GroovyFile)psiFile);
    }

    return null;
  }

  @Nullable
  public static GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile) {
    String fileName = groovyFile.getName();
    int idx = fileName.lastIndexOf('.');
    if (idx < 0) return null;

    return getClassDefinition(groovyFile, fileName.substring(0, idx));
  }

  @Nullable
  public static GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile, @NotNull String classSimpleName) {
    for (GrTypeDefinition definition : (groovyFile).getTypeDefinitions()) {
      if (classSimpleName.equals(definition.getName())) {
        return definition;
      }
    }

    return null;
  }

  public static File getBundledGroovyJar() {
    String root = new File(PathUtil.getJarPathForClass(GroovyUtils.class)).isDirectory() ?
                  PluginPathManager.getPluginHomePath("groovy") + "/../../lib/" : PathManager.getHomePath() + "/lib/";
    final File[] groovyJars = GroovyConfigUtils.getGroovyAllJars(root);
    assert groovyJars.length == 1;
    return groovyJars[0];
  }
}