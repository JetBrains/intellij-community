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
package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.AbstractIdeaSpecificSettings;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.module.JpsDependenciesList;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.util.List;

/**
* User: anna
* Date: 11/8/12
*/
class JpsIdeaSpecificSettings extends AbstractIdeaSpecificSettings<JpsModule, String> {
  private JpsMacroExpander myExpander;

  JpsIdeaSpecificSettings(JpsMacroExpander expander) {
    myExpander = expander;
  }

  @Override
  protected String[] getEntries(JpsModule model) {
    final List<String> urls = model.getContentRootsList().getUrls();
    return ArrayUtil.toStringArray(urls);
  }

  @Override
  protected String createContentEntry(JpsModule model, String url) {
    model.getContentRootsList().addUrl(url);
    return url;
  }

  @Override
  protected void setupLibraryRoots(Element root, JpsModule model) {}

  @Override
  protected void setupJdk(Element root, JpsModule model) {
    final String inheritJdk = root.getAttributeValue("inheritJdk");
    if (inheritJdk != null && Boolean.parseBoolean(inheritJdk)) {
      final JpsDependenciesList dependenciesList = model.getDependenciesList();
      dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
    }
    else {
      final String jdkName = root.getAttributeValue("jdk");
      if (jdkName != null) {
        JpsSdkTableSerializer.setSdkReference(model.getSdkReferencesTable(), jdkName, JpsJavaSdkType.INSTANCE);
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

    final String inheritedOutput = root.getAttributeValue(IdeaXml.INHERIT_COMPILER_OUTPUT_ATTR);
    if (inheritedOutput != null && Boolean.valueOf(inheritedOutput).booleanValue()) {
      extension.setInheritOutput(true);
    }
    extension.setExcludeOutput(root.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null);
  }

  @Override
  protected void readLanguageLevel(Element root, JpsModule model) throws InvalidDataException {}

  @Override
  protected void expandElement(Element root, JpsModule model) {
    myExpander.substitute(root, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  protected void overrideModulesScopes(Element root, JpsModule model) {}

  @Override
  public void readContentEntry(Element root, String contentUrl, JpsModule model) {
    for (Object o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
      final String url = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
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
  }

  private static JpsJavaExtensionService getService() {
    return JpsJavaExtensionService.getInstance();
  }
}
