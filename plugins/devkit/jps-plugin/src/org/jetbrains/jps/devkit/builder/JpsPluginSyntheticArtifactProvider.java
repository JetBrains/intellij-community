// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.devkit.model.JpsIdeaSdkProperties;
import org.jetbrains.jps.devkit.model.JpsIdeaSdkType;
import org.jetbrains.jps.devkit.model.JpsPluginModuleProperties;
import org.jetbrains.jps.devkit.model.JpsPluginModuleType;
import org.jetbrains.jps.incremental.artifacts.JpsSyntheticArtifactProvider;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JpsPluginSyntheticArtifactProvider extends JpsSyntheticArtifactProvider {
  private static final Logger LOG = Logger.getInstance(JpsPluginSyntheticArtifactProvider.class);

  @NotNull
  @Override
  public List<JpsArtifact> createArtifacts(@NotNull JpsModel model) {
    List<JpsArtifact> artifacts = new ArrayList<>();
    for (JpsTypedModule<JpsSimpleElement<JpsPluginModuleProperties>> module : model.getProject().getModules(JpsPluginModuleType.INSTANCE)) {
      artifacts.add(createArtifact(module, module.getProperties().getData()));
    }
    return artifacts;
  }

  private static JpsArtifact createArtifact(JpsModule module, JpsPluginModuleProperties properties) {
    JpsPackagingElementFactory factory = JpsPackagingElementFactory.getInstance();
    JpsCompositePackagingElement root = factory.createArtifactRoot();
    String pluginXmlUrl = properties.getPluginXmlUrl();
    if (pluginXmlUrl != null) {
      String pluginXmlPath = JpsPathUtil.urlToPath(pluginXmlUrl);
      JpsCompositePackagingElement metaInfDir = factory.getOrCreateDirectory(root, "META-INF");
      metaInfDir.addChild(factory.createFileCopy(pluginXmlPath, null));
      File pluginXmlFile = JpsPathUtil.urlToFile(pluginXmlUrl);
      if (pluginXmlFile.exists()) {
        try {
          Element rootElement = JDOMUtil.load(pluginXmlFile);
          for (Element dependsElement : JDOMUtil.getChildren(rootElement, "depends")) {
            String relativePath = dependsElement.getAttributeValue("config-file");
            if (relativePath != null) {
              File dependencyFile = new File(pluginXmlFile.getParent(), FileUtil.toSystemDependentName(relativePath));
              String dependencyPath = FileUtil.toSystemIndependentName(dependencyFile.getAbsolutePath());
              metaInfDir.addChild(factory.createFileCopy(dependencyPath, null));
            }
          }
        }
        catch (JDOMException | IOException e) {
          LOG.info(e);
        }
      }
    }

    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively().includedIn(
      JpsJavaClasspathKind.PRODUCTION_RUNTIME);
    JpsCompositePackagingElement classesDir = factory.getOrCreateDirectory(root, "classes");
    for (JpsModule depModule : enumerator.getModules()) {
      if (depModule.getModuleType().equals(JpsJavaModuleType.INSTANCE)) {
        classesDir.addChild(JpsJavaExtensionService.getInstance().createProductionModuleOutput(depModule.createReference()));
      }
    }
    classesDir.addChild(JpsJavaExtensionService.getInstance().createProductionModuleOutput(module.createReference()));

    for (JpsLibrary library : enumerator.getLibraries()) {
      JpsCompositePackagingElement parent;
      if (hasDirsOnly(library)) {
        parent = classesDir;
      }
      else {
        parent = factory.getOrCreateDirectory(root, "lib");
      }
      parent.addChild(factory.createLibraryElement(library.createReference()));
      for (File nativeRoot : library.getFiles(JpsNativeLibraryRootType.INSTANCE)) {
        JpsPackagingElement copy;
        if (nativeRoot.isDirectory()) {
          copy = factory.createDirectoryCopy(nativeRoot.getAbsolutePath());
        }
        else {
          copy = factory.createFileCopy(nativeRoot.getAbsolutePath(), null);
        }
        factory.getOrCreateDirectory(root, "lib").addChild(copy);
      }
    }

    String name = module.getName() + ":plugin";
    JpsArtifact artifact = JpsArtifactService.getInstance().createArtifact(name, root, DirectoryArtifactType.INSTANCE, JpsElementFactory.getInstance().createDummyElement());

    JpsSdk<JpsSimpleElement<JpsIdeaSdkProperties>> sdk = module.getSdk(JpsIdeaSdkType.INSTANCE);
    if (sdk != null) {
      String sandboxHome = sdk.getSdkProperties().getData().getSandboxHome();
      if (sandboxHome != null) {
        artifact.setOutputPath(sandboxHome + "/plugins/" + module.getName());
      }
    }
    return artifact;
  }

  private static boolean hasDirsOnly(JpsLibrary library) {
    List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
    for (File file : files) {
      if (!file.isDirectory()) {
        return false;
      }
    }
    return true;
  }
}
