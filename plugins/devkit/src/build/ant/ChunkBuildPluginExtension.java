/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package org.jetbrains.idea.devkit.build.ant;

import com.intellij.compiler.ant.*;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.compiler.DummyCompileContext;
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

  public boolean haveSelfOutputs(Module[] modules) {
    return true;
  }

  @NotNull
  public String[] getTargets(final ModuleChunk chunk) {
    return isPlugins(chunk.getModules()) ? new String[] {"plugin.build."+ BuildProperties.convertName(chunk.getName())}
           : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator) {

    final Module[] modules = chunk.getModules();
    if (isPlugins(modules)) {
      final BuildTargetsFactory factory = BuildTargetsFactory.getInstance();
      final Module module = modules[0];
      PluginBuildConfiguration buildProperties = PluginBuildConfiguration.getInstance(module);
      String configurationName = module.getName();
      ExplodedAndJarTargetParameters parameters =
        new ExplodedAndJarTargetParameters(chunk, module, genOptions, buildProperties, 
                                           PluginBuildProperties.PLUGIN_DIR_EXPLODED, PluginBuildProperties.PLUGIN_PATH_JAR,
                                           PluginBuildProperties.getBuildExplodedTargetName(configurationName),
                                           PluginBuildProperties.getBuildJarTargetName(configurationName),
                                           PluginBuildProperties.getExplodedPathProperty(configurationName),
                                           PluginBuildProperties.getJarPathProperty(configurationName), null);
      final Set<Library> libs = new HashSet<Library>();
      PluginBuildUtil.getLibraries(module, libs);
      @NonNls String jarPath = chunk.getBaseDir().getPath() + "/" + chunk.getName();
      if (libs.isEmpty()) {
        jarPath += ".jar";
      } else {
        jarPath += ".zip";
      }
      @NonNls final String buildTargetName = PluginBuildProperties.getBuildPluginTarget(module);
      generator.add(factory.createCompositeBuildTarget(parameters, buildTargetName,
                                                       DevKitBundle.message("ant.build.description", module.getName()),
                                                       BuildProperties.getCompileTargetName(module.getName()), jarPath));

      generator.add(factory.createComment(DevKitBundle.message("ant.exploded.comment", chunk.getName(), PluginBuildProperties.PLUGIN_DIR_EXPLODED)), 1);
      final BuildRecipe buildRecipe = buildProperties.getBuildParticipant().getBuildInstructions(DummyCompileContext.getInstance());
      generator.add(factory.createBuildExplodedTarget(parameters, buildRecipe,
                                                      DevKitBundle.message("ant.exploded.description", module.getName())));

      generator.add(factory.createComment(DevKitBundle.message("ant.build.jar.comment", chunk.getName(), PluginBuildProperties.PLUGIN_PATH_JAR)), 1);
      generator.add(new BuildJarTarget(chunk, genOptions, buildProperties));
    }
  }

  private static boolean isPlugins(final Module[] modules) {
    return modules.length == 1 && PluginModuleType.isOfType(modules[0]);
  }

}