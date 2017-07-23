/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.util.Arrays;

public class GroovyFacetUtil {
  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";

  public static boolean tryToSetUpGroovyFacetOnTheFly(final Module module) {
    final Project project = module.getProject();
    GroovyConfigUtils utils = GroovyConfigUtils.getInstance();
    final Library[] libraries = utils.getAllSDKLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages
        .showOkCancelDialog(
          GroovyBundle.message("groovy.like.library.found.text", module.getName(), library.getName(), utils.getSDKLibVersion(library)),
          GroovyBundle.message("groovy.like.library.found"), JetgroovyIcons.Groovy.Groovy_32x32);
      if (result == Messages.OK) {
        WriteAction.run(() -> {
          ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
          model.commit();
        });
        return true;
      }
    }
    return false;
  }

  public static boolean isSuitableModule(Module module) {
    if (module == null) return false;
    return isAcceptableModuleType(ModuleType.get(module));
  }

  public static boolean isAcceptableModuleType(ModuleType type) {
    return type instanceof JavaModuleType || PLUGIN_MODULE_ID.equals(type.getId()) || "ANDROID_MODULE".equals(type.getId());
  }

  public static File getBundledGroovyJar() {
    final File[] groovyJars = LibrariesUtil.getFilesInDirectoryByPattern(getLibDirectory(), GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
    assert groovyJars.length == 1 : Arrays.asList(groovyJars);
    return groovyJars[0];
  }

  public static String getLibDirectory() {
    if (new File(PathUtil.getJarPathForClass(GroovyFacetUtil.class)).isDirectory()) {
      return FileUtil.toCanonicalPath(PluginPathManager.getPluginHomePath("groovy") + "/../../lib/");
    }
    return PathManager.getHomePath() + "/lib/";
  }
}
