// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.AbstractIdeaSpecificSettings;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsDependenciesList;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.io.File;
import java.util.Map;

class JpsIdeaSpecificSettings extends AbstractIdeaSpecificSettings<JpsModule, String, JpsSdkType<?>> {
  private final JpsMacroExpander myExpander;

  JpsIdeaSpecificSettings(JpsMacroExpander expander) {
    myExpander = expander;
  }

  @Override
  protected void readLibraryLevels(Element root, @NotNull Map<String, String> levels) {
    final Element levelsElement = root.getChild("levels");
    if (levelsElement != null) {
      for (Element element : levelsElement.getChildren("level")) {
        String libName = element.getAttributeValue("name");
        String libLevel = element.getAttributeValue("value");
        if (libName != null && libLevel != null) {
          levels.put(libName, libLevel);
        }
      }
    }
  }

  @Override
  protected String[] getEntries(JpsModule model) {
    return ArrayUtilRt.toStringArray(model.getContentRootsList().getUrls());
  }

  @Override
  protected String createContentEntry(JpsModule model, String url) {
    model.getContentRootsList().addUrl(url);
    return url;
  }

  @Override
  protected void setupLibraryRoots(Element root, JpsModule model) {}

  @Override
  protected void setupJdk(Element root, JpsModule model, @Nullable JpsSdkType<?> projectSdkType) {
    final String inheritJdk = root.getAttributeValue("inheritJdk");
    final JpsDependenciesList dependenciesList = model.getDependenciesList();
    if (inheritJdk != null && Boolean.parseBoolean(inheritJdk)) {
      dependenciesList.addSdkDependency(projectSdkType != null ? projectSdkType : JpsJavaSdkType.INSTANCE);
    }
    else {
      final String jdkName = root.getAttributeValue("jdk");
      if (jdkName != null) {
        String jdkType = root.getAttributeValue("jdk_type");
        JpsSdkType<?> sdkType = null;
        if (jdkType != null) {
          sdkType = JpsSdkTableSerializer.getSdkType(jdkType);
        }
        if (sdkType == null) {
          sdkType = JpsJavaSdkType.INSTANCE;
        }
        dependenciesList.addSdkDependency(sdkType);
        JpsSdkTableSerializer.setSdkReference(model.getSdkReferencesTable(), jdkName, sdkType);
        if (sdkType instanceof JpsJavaSdkTypeWrapper) {
          dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
        }
      }
    }
  }

  @Override
  protected void setupCompilerOutputs(Element root, JpsModule model) {
    final JpsJavaModuleExtension extension = getService().getOrCreateModuleExtension(model);
    final Element testOutputElement = root.getChild(IdeaXml.OUTPUT_TEST_TAG);
    if (testOutputElement != null) {
      extension.setTestOutputUrl(testOutputElement.getAttributeValue(IdeaXml.URL_ATTR));
    }

    final String inheritedOutput = root.getAttributeValue(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE);
    if (inheritedOutput != null && Boolean.parseBoolean(inheritedOutput)) {
      extension.setInheritOutput(true);
    }
    extension.setExcludeOutput(root.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null);
  }

  @Override
  protected void readLanguageLevel(Element root, JpsModule model) {
    final String languageLevel = root.getAttributeValue("LANGUAGE_LEVEL");
    final JpsJavaModuleExtension extension = getService().getOrCreateModuleExtension(model);
    if (languageLevel != null) {
      extension.setLanguageLevel(LanguageLevel.valueOf(languageLevel));
    }
  }

  @Override
  protected void expandElement(Element root, JpsModule model) {
    myExpander.substitute(root, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  protected void overrideModulesScopes(Element root, JpsModule model) {}

  @Override
  public void readContentEntry(Element root, String contentUrl, JpsModule model) {
    for (Element o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
      final String url = o.getAttributeValue(IdeaXml.URL_ATTR);
      JpsModuleSourceRoot folderToBeTest = null;
      for (JpsModuleSourceRoot folder : model.getSourceRoots()) {
        if (Comparing.strEqual(folder.getUrl(), url)) {
          folderToBeTest = folder;
          break;
        }
      }
      if (folderToBeTest != null) {
        model.removeSourceRoot(folderToBeTest.getUrl(), JavaSourceRootType.SOURCE);
      }
      model.addSourceRoot(url, JavaSourceRootType.TEST_SOURCE);
    }

    for (Element o : root.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)) {
      final String excludeUrl = o.getAttributeValue(IdeaXml.URL_ATTR);
      if (FileUtil.isAncestor(new File(contentUrl), new File(excludeUrl), false)) {
        model.getExcludeRootsList().addUrl(excludeUrl);
      }
    }

    for (Element ppElement : root.getChildren(IdeaXml.PACKAGE_PREFIX_TAG)) {
      final String prefix = ppElement.getAttributeValue(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR);
      final String url = ppElement.getAttributeValue(IdeaXml.URL_ATTR);
      for (JpsModuleSourceRoot sourceRoot : model.getSourceRoots()) {
        if (Comparing.strEqual(sourceRoot.getUrl(), url)) {
          JpsElement properties = sourceRoot.getProperties();
          if (properties instanceof JavaSourceRootProperties) {
            ((JavaSourceRootProperties)properties).setPackagePrefix(prefix);
          }
          break;
        }
      }
    }
  }

  private static JpsJavaExtensionService getService() {
    return JpsJavaExtensionService.getInstance();
  }
}
