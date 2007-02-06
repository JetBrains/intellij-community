/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.BuildParticipantBase;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.LibraryLink;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.HashSet;

/**
 * @author peter
*/
public class PluginBuildParticipant extends BuildParticipantBase {
  @NonNls private static final String CLASSES = "/classes";
  @NonNls private static final String LIB = "/lib/";
  @NonNls private static final String LIB_DIRECTORY = "lib";


  public void registerBuildInstructions(final Module module, final BuildRecipe instructions, final CompileContext context) {
    if (module.getModuleType() != PluginModuleType.getInstance()) {
      return;
    }
    ProjectJdk jdk = IdeaJdk.findIdeaJdk(ModuleRootManager.getInstance(module).getJdk());
    if (jdk != null && IdeaJdk.isFromIDEAProject(jdk.getHomePath())) {
      return;
    }

    super.registerBuildInstructions(module, instructions, context);

    if (jdk == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("jdk.type.incorrect", module.getName()), null, -1, -1);
      return;
    }

    final Module[] wrongSetDependencies = PluginBuildUtil.getWrongSetDependencies(module);
    if (wrongSetDependencies.length != 0) {
      boolean realProblems = false;
      final String pluginId = DescriptorUtil.getPluginId(module);

      for (Module dependency : wrongSetDependencies) {
        if (!PluginModuleType.isOfType(dependency)) {
          realProblems = true;
          context.addMessage(CompilerMessageCategory.ERROR,
                             DevKitBundle.message("incorrect.dependency.non-plugin-module", dependency.getName(), module.getName()), null,
                             -1, -1);
        }
        else {
          final XmlFile pluginXml = PluginModuleType.getPluginXml(dependency);
          boolean isDeclared = false;
          if (pluginXml != null) {
            final XmlTag rootTag = pluginXml.getDocument().getRootTag();
            final XmlTag[] dependencies = rootTag != null ? rootTag.findSubTags("depends") : XmlTag.EMPTY;
            for (XmlTag dep : dependencies) {
              if (dep.getValue().getTrimmedText().equals(pluginId)) {
                isDeclared = true;
                break;
              }
            }
          }
          if (!isDeclared) {
            // make this a warning instead?
            realProblems = true;
            context.addMessage(CompilerMessageCategory.ERROR,
                               DevKitBundle.message("incorrect.dependency.not-declared", dependency.getName(), module.getName()), null, -1,
                               -1);
          }
        }
      }
      if (realProblems) return;
    }

    final PluginBuildConfiguration moduleBuild = PluginBuildConfiguration.getInstance(module);
    final String explodedPath = moduleBuild.getExplodedPath();
    if (explodedPath == null) return; //where to put everything?
    HashSet<Module> modules = new HashSet<Module>();
    PluginBuildUtil.getDependencies(module, modules);

    ModuleLink[] containingModules = new ModuleLink[modules.size()];
    int i = 0;
    final DeploymentUtil makeUtil = DeploymentUtil.getInstance();
    for (Module dep : modules) {
      ModuleLink link = makeUtil.createModuleLink(dep, module);
      containingModules[i++] = link;
      link.setPackagingMethod(PackagingMethod.COPY_FILES);
      link.setURI(CLASSES);
    }

    // output may be excluded, copy it nevertheless
    makeUtil.addModuleOutputContents(context, instructions, module, module, CLASSES, explodedPath, null);

    // child Java utility modules
    makeUtil.addJavaModuleOutputs(module, containingModules, instructions, context, explodedPath);

    HashSet<Library> libs = new HashSet<Library>();
    PluginBuildUtil.getLibraries(module, libs);
    for (Module dependentModule : modules) {
      PluginBuildUtil.getLibraries(dependentModule, libs);
    }

    final LibraryLink[] libraryLinks = new LibraryLink[libs.size()];
    i = 0;
    for (Library library : libs) {
      LibraryLink link = makeUtil.createLibraryLink(library, module);
      libraryLinks[i++] = link;
      link.setPackagingMethod(PackagingMethod.COPY_FILES);
      final String singleFileName = link.getSingleFileName();
      final boolean onlyDirs = link.hasDirectoriesOnly();
      if (onlyDirs) {//todo split one lib into 2 separate libs if there are jars and dirs
        link.setURI(CLASSES);
      }
      else {
        if (singleFileName != null) {
          link.setURI(LIB + singleFileName);
        }
        else {
          link.setURI(LIB);
        }
      }
    }

    // libraries
    final VirtualFile libDir = VfsUtil.findRelativeFile(LIB_DIRECTORY, jdk.getHomeDirectory());
    for (i = 0; i < libraryLinks.length; i++) {
      LibraryLink libraryLink = libraryLinks[i];
      VirtualFile[] files = libraryLink.getLibrary().getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file.getFileSystem() instanceof JarFileSystem) {
          file = ((JarFileSystem)file.getFileSystem()).getVirtualFileForJar(file);
        }
        if (VfsUtil.isAncestor(libDir, file, false)) {
          context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("dont.add.idea.libs.to.classpath", file.getName()), null,
                             -1, -1);
        }
      }
      makeUtil.addLibraryLink(context, instructions, libraryLink, module, explodedPath);
    }
  }

  protected ConfigFile[] getDeploymentDescriptors(final Module module) {
    final PluginBuildConfiguration moduleBuildProperties = (PluginBuildConfiguration)PluginBuildConfiguration.getInstance(module);
    if (moduleBuildProperties == null) {
      return ConfigFile.EMPTY_ARRAY;
    }
    return new ConfigFile[]{moduleBuildProperties.getPluginXML()};
  }

}
