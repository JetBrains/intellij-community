/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public class GriffonBuilderExtension implements GroovyBuilderExtension {

  @NotNull
  @Override
  public Collection<String> getCompilationClassPath(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<String> getCompilationUnitPatchers(@NotNull CompileContext context, @NotNull ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      if (shouldInjectGriffon(module)) {
        return Collections.singleton("org.jetbrains.groovy.compiler.rt.GriffonInjector");
      }
    }
    return Collections.emptyList();
  }

  private static boolean shouldInjectGriffon(JpsModule module) {
    for (String rootUrl : module.getContentRootsList().getUrls()) {
      File root = JpsPathUtil.urlToFile(rootUrl);
      if (new File(root, "griffon-app").isDirectory() &&
          new File(root, "application.properties").isFile()) {
        return true;
      }
    }

    return false;
  }

}
