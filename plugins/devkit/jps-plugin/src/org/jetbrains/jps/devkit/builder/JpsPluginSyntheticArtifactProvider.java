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
package org.jetbrains.jps.devkit.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.util.JpsPathUtil;
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
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsPluginSyntheticArtifactProvider extends JpsSyntheticArtifactProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.devkit.builder.JpsPluginSyntheticArtifactProvider");

  @NotNull
  @Override
  public List<JpsArtifact> createArtifacts(@NotNull JpsModel model) {
    List<JpsArtifact> artifacts = new ArrayList<JpsArtifact>();
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
          Element rootElement = JDOMUtil.loadDocument(pluginXmlFile).getRootElement();
          for (Element dependsElement : JDOMUtil.getChildren(rootElement, "depends")) {
            String relativePath = dependsElement.getAttributeValue("config-file");
            if (relativePath != null) {
              File dependencyFile = new File(pluginXmlFile.getParent(), FileUtil.toSystemDependentName(relativePath));
              String dependencyPath = FileUtil.toSystemIndependentName(dependencyFile.getAbsolutePath());
              metaInfDir.addChild(factory.createFileCopy(dependencyPath, null));
            }
          }
        }
        catch (JDOMException e) {
          LOG.info(e);
        }
        catch (IOException e) {
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
