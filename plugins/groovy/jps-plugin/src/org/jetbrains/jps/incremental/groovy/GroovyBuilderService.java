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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class GroovyBuilderService extends BuilderService {
  /**
   * All Groovy-Eclipse stuff is contained in a separate classLoader to avoid clashes with ecj.jar being in the classpath of the builder process
   */
  @Nullable
  private static final ClassLoader ourGreclipseLoader = createGreclipseLoader();

  @Nullable
  private static ClassLoader createGreclipseLoader() {
    String jar = System.getProperty("groovy.eclipse.batch.jar");
    if (jar == null) return null;

    try {
      URL[] urls = {
        new File(jar).toURI().toURL(),
        new File(ObjectUtils.assertNotNull(PathManager.getJarPathForClass(GreclipseMain.class))).toURI().toURL()
      };
      ClassLoader loader = new URLClassLoader(urls, null);
      Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", false, loader);
      return loader;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    if (ourGreclipseLoader != null) {
      return Arrays.asList(new GreclipseBuilder(ourGreclipseLoader));
    }
    return Arrays.asList(new GroovyBuilder(true), new GroovyBuilder(false));
  }

}
