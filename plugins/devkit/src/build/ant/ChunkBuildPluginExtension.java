/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package org.jetbrains.idea.devkit.build.ant;

import com.intellij.compiler.ant.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.HashSet;
import java.util.Set;

public class ChunkBuildPluginExtension extends ChunkBuildExtension {

  public boolean haveSelfOutputs(Module[] modules) {
    return true;
  }

  @Nullable
  public String getAssemblingName(final Module[] modules, final String name) {
    return isPlugins(modules) ? "plugin.build."+ BuildProperties.convertName(name) : null;
  }

  public void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator) {

    final Module[] modules = chunk.getModules();
    if (isPlugins(modules)) {
      final BuildTargetsFactory factory = BuildTargetsFactory.getInstance();
      final @NonNls String explodedPathProperty = "plugin.dir.exploded";
      final @NonNls String jarPathProperty = "plugin.path.jar";
      PluginBuildConfiguration buildProperties = PluginBuildConfiguration.getInstance(modules[0]);
      factory.init(chunk, buildProperties, genOptions, explodedPathProperty, new Function<String, String>() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        public String fun(final String name) {
          return "plugin.build.exploded." + BuildProperties.convertName(name);
        }
      }, new Function<String, String>() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        public String fun(final String name) {
          return BuildProperties.convertName(name) + ".plugin.exploded.dir";
        }
      }, jarPathProperty, new Function<String, String>() {
        @SuppressWarnings({"HardCodedStringLiteral"})
        public String fun(final String name) {
          return "plugin.build.jar." + BuildProperties.convertName(name);
        }
      });
      final Set<Library> libs = new HashSet<Library>();
      PluginBuildUtil.getLibraries(modules[0], libs);
      @NonNls String jarPath = chunk.getBaseDir().getPath() + "/" + chunk.getName();
      if (libs.isEmpty()) {
        jarPath += ".jar";
      } else {
        jarPath += ".zip";
      }
      generator.add(factory.createCompositeBuildTarget("plugin.build." + BuildProperties.convertName(factory.getModuleName()),
                                                       DevKitBundle.message("ant.build.description", chunk.getName()), new Function<Module, String>() {
        @Nullable
        public String fun(final Module module) {
          return BuildProperties.getCompileTargetName(module.getName());
        }

      }, jarPath));

      generator.add(factory.createComment(DevKitBundle.message("ant.exploded.comment", chunk.getName(), explodedPathProperty)), 1);
      generator.add(factory.createBuildExplodedTarget(DevKitBundle.message("ant.exploded.description") + chunk.getName() + "\'"));

      generator.add(factory.createComment(DevKitBundle.message("ant.build.jar.comment", chunk.getName(), jarPathProperty)), 1);
      generator.add(new BuildJarTarget(chunk, genOptions, (PluginBuildConfiguration)factory.getModuleBuildProperties()));
    }
  }

  private static boolean isPlugins(final Module[] modules) {
    return modules.length == 1 && PluginModuleType.isOfType(modules[0]);
  }

}