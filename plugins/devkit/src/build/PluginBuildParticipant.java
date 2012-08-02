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
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactImpl;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.HashSet;
import java.util.List;

/**
 * @author peter
*/
public class PluginBuildParticipant extends BuildParticipant {
  @NonNls private static final String CLASSES = "/classes";
  @NonNls private static final String LIB = "/lib/";
  @NonNls private static final String LIB_DIRECTORY = "lib";
  private final Module myModule;
  private final PluginBuildConfiguration myPluginBuildConfiguration;

  public PluginBuildParticipant(final Module module, final PluginBuildConfiguration pluginBuildConfiguration) {
    super();
    myModule = module;
    myPluginBuildConfiguration = pluginBuildConfiguration;
  }

  @Override
  public Artifact createArtifact(CompileContext context) {
    Sdk jdk = IdeaJdk.findIdeaJdk(ModuleRootManager.getInstance(myModule).getSdk());
    if (jdk != null && IdeaJdk.isFromIDEAProject(jdk.getHomePath())) {
      return null;
    }

    if (jdk == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("jdk.type.incorrect", myModule.getName()), null, -1, -1);
      return null;
    }

    final String outputPath = PluginBuildUtil.getPluginExPath(myModule);
    if (outputPath == null) {
      return null;
    }

    if (!checkDependencies(context)) {
      return null;
    }


    final PackagingElementFactory factory = PackagingElementFactory.getInstance();
    final ArtifactRootElement<?> root = factory.createArtifactRootElement();

    ConfigFile configFile = myPluginBuildConfiguration.getPluginXML();
    if (configFile != null) {
      DeploymentUtil.getInstance().checkConfigFile(configFile, context, myModule);
      factory.addFileCopy(root, "META-INF/", VfsUtil.urlToPath(configFile.getUrl()));

      final XmlFile xmlFile = configFile.getXmlFile();
      if (xmlFile != null) {
        final XmlDocument document = xmlFile.getDocument();
        if (document != null) {
          final DomElement domElement = DomManager.getDomManager(xmlFile.getProject()).getDomElement(document.getRootTag());
          if (domElement instanceof IdeaPlugin) {
            for(Dependency dependency: ((IdeaPlugin)domElement).getDependencies()) {
              final String file = dependency.getConfigFile().getValue();
              if (file != null) {
                final VirtualFile virtualFile = configFile.getVirtualFile();
                assert virtualFile != null;
                final VirtualFile parent = virtualFile.getParent();
                assert parent != null;
                final String url = parent.getUrl();
                factory.addFileCopy(root, "META-INF/", VfsUtil.urlToPath(url) + "/" + file);
              }
            }
          }
        }
      }
    }

    HashSet<Module> modules = new HashSet<Module>();
    PluginBuildUtil.getDependencies(myModule, modules);

    final CompositePackagingElement<?> classesDir = factory.getOrCreateDirectory(root, CLASSES);
    for (Module dep : modules) {
      classesDir.addOrFindChild(factory.createModuleOutput(dep));
    }
    classesDir.addOrFindChild(factory.createModuleOutput(myModule));

    HashSet<Library> libs = new HashSet<Library>();
    PluginBuildUtil.getLibraries(myModule, libs);
    for (Module dependentModule : modules) {
      PluginBuildUtil.getLibraries(dependentModule, libs);
    }


    // libraries
    final VirtualFile libDir = jdk.getHomeDirectory().findFileByRelativePath(LIB_DIRECTORY);
    for (Library library : libs) {
      boolean hasDirsOnly = true;
      VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file.getFileSystem() instanceof JarFileSystem) {
          hasDirsOnly = false;
          file = ((JarFileSystem)file.getFileSystem()).getVirtualFileForJar(file);
        }
        if (libDir != null && file != null && VfsUtilCore.isAncestor(libDir, file, false)) {
          context.addMessage(CompilerMessageCategory.ERROR, DevKitBundle.message("dont.add.idea.libs.to.classpath", file.getName()), null,
                             -1, -1);
        }
      }

      final List<? extends PackagingElement<?>> elements = factory.createLibraryElements(library);
      if (hasDirsOnly) {
        //todo split one lib into 2 separate libs if there are jars and dirs
        classesDir.addOrFindChildren(elements);
      }
      else {
        factory.getOrCreateDirectory(root, LIB).addOrFindChildren(elements);
      }
    }
    
    return new ArtifactImpl(getArtifactName(), PlainArtifactType.getInstance(), false, root, FileUtil.toSystemIndependentName(outputPath));
  }

  private String getArtifactName() {
    return myModule.getName() + ":plugin";
  }

  private boolean checkDependencies(CompileContext context) {
    final Module[] wrongSetDependencies = PluginBuildUtil.getWrongSetDependencies(myModule);
    if (wrongSetDependencies.length != 0) {
      boolean realProblems = false;
      final String pluginId = DescriptorUtil.getPluginId(myModule);

      for (Module dependency : wrongSetDependencies) {
        if (!PluginModuleType.isOfType(dependency)) {
          realProblems = true;
          context.addMessage(CompilerMessageCategory.ERROR,
                             DevKitBundle.message("incorrect.dependency.non-plugin-module", dependency.getName(), myModule.getName()), null,
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
                               DevKitBundle.message("incorrect.dependency.not-declared", dependency.getName(), myModule.getName()), null, -1,
                               -1);
          }
        }
      }
      if (realProblems) return false;
    }
    return true;
  }

}
