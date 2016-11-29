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

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package org.jetbrains.idea.devkit.build.ant;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.HashSet;
import java.util.Set;

public class ChunkBuildPluginExtension extends ChunkBuildExtension {

  @NotNull
  public String[] getTargets(final ModuleChunk chunk) {
    return isPlugins(chunk.getModules()) ? new String[] {PluginBuildProperties.getBuildJarTargetName(chunk.getName())}
           : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator) {

    final Module[] modules = chunk.getModules();
    if (isPlugins(modules)) {
      final BuildTargetsFactory factory = BuildTargetsFactory.getInstance();
      final Module module = modules[0];
      PluginBuildConfiguration buildProperties = PluginBuildConfiguration.getInstance(module);

      final Set<Library> libs = new HashSet<>();
      PluginBuildUtil.getLibraries(module, libs);
      @NonNls String jarPath = chunk.getBaseDir().getPath() + "/" + chunk.getName();
      if (libs.isEmpty()) {
        jarPath += ".jar";
      } else {
        jarPath += ".zip";
      }

      generator.add(new Property(PluginBuildProperties.getJarPathProperty(chunk.getName()), GenerationUtils.toRelativePath(jarPath, chunk, genOptions)), 1);

      generator.add(factory.createComment(DevKitBundle.message("ant.build.jar.comment", chunk.getName())), 1);
      generator.add(new BuildJarTarget(chunk, genOptions, buildProperties));
    }
  }

  private static boolean isPlugins(final Module[] modules) {
    return modules.length == 1 && PluginModuleType.isOfType(modules[0]);
  }

}