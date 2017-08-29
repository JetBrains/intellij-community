/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions.service;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/actions/newService/")
public class ServiceCreatorTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/actions/newService";
  }


  public void testCreateApplicationServiceInterfaceAndImplementation() {
    doTestCreateInterfaceAndImplementation("my.plugin.ApplicationServiceInterface", "my.plugin.impl.ApplicationServiceImpl",
                                           "ApplicationServiceInterface.java", "ApplicationServiceImplementation.java",
                                           "applicationService");
  }

  public void testCreateProjectServiceInterfaceAndImplementation() {
    doTestCreateInterfaceAndImplementation("my.plugin.ProjectServiceInterface", "my.plugin.impl.ProjectServiceImpl",
                                           "ProjectServiceInterface.java", "ProjectServiceImplementation.java", "projectService");
  }

  public void testCreateModuleServiceInterfaceAndImplementation() {
    doTestCreateInterfaceAndImplementation("my.plugin.ModuleServiceInterface", "my.plugin.impl.ModuleServiceImpl",
                                           "ModuleServiceInterface.java", "ModuleServiceImplementation.java", "moduleService");
  }

  public void testCreateApplicationServiceOnlyImplementation() {
    doTestCreateOnlyImplementation("my.plugin.ApplicationServiceClass", "ApplicationServiceClass.java", "applicationService");
  }

  public void testCreateProjectServiceOnlyImplementation() {
    doTestCreateOnlyImplementation("my.plugin.ProjectServiceClass", "ProjectServiceClass.java", "projectService");
  }

  public void testCreateModuleServiceOnlyImplementation() {
    doTestCreateOnlyImplementation("my.plugin.ModuleServiceClass", "ModuleServiceClass.java", "moduleService");
  }


  private void doTestCreateInterfaceAndImplementation(String interfaceFqName, String implementationFqName,
                                                     String interfaceTemplate, String implementationTemplate, String tagName) {
    VirtualFile copied = myFixture.copyDirectoryToProject("", "");
    PsiDirectory dir = myFixture.getPsiManager().findDirectory(copied);
    XmlFile pluginXml = PluginModuleType.getPluginXml(myFixture.getModule());

    NewServiceActionBase.ServiceCreator creator = new NewServiceActionBase.ServiceCreator(
      dir, interfaceTemplate, implementationTemplate, null, tagName);
    boolean created = creator.createInterfaceAndImplementation(interfaceFqName, implementationFqName, pluginXml);
    assertTrue(created);

    PsiClass[] createdClasses = creator.getCreatedClasses();
    assertNotNull(createdClasses);
    assertSize(2, createdClasses);

    PsiClass createdInterface = createdClasses[0];
    PsiClass createdImplementation = createdClasses[1];

    assertEquals(interfaceFqName.substring(interfaceFqName.lastIndexOf(".") + 1), createdInterface.getName());
    assertEquals(implementationFqName.substring(implementationFqName.lastIndexOf(".") + 1), createdImplementation.getName());

    PsiReferenceList implementsList = createdImplementation.getImplementsList();
    assertNotNull(implementsList);
    PsiJavaCodeReferenceElement[] elements = implementsList.getReferenceElements();
    assertNotNull(elements);
    PsiJavaCodeReferenceElement element = elements[0];
    assertEquals(interfaceFqName, element.getQualifiedName());

    DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(getProject()).getFileElement(pluginXml, IdeaPlugin.class);
    assertNotNull(fileElement);
    IdeaPlugin ideaPlugin = fileElement.getRootElement();
    List<Extensions> extensionsList = ideaPlugin.getExtensions();
    assertNotNull(extensionsList);
    assertEquals(1, extensionsList.size());

    XmlTag extensions = extensionsList.get(0).getXmlTag();
    assertNotNull(extensions);
    XmlTag[] extensionTags = extensions.getSubTags();
    assertNotNull(extensionTags);
    assertSize(1, extensionTags);

    XmlTag serviceTag = extensionTags[0];
    assertEquals(tagName, serviceTag.getName());
    assertEquals(interfaceFqName, serviceTag.getAttributeValue("serviceInterface"));
    assertEquals(implementationFqName, serviceTag.getAttributeValue("serviceImplementation"));
  }

  private void doTestCreateOnlyImplementation(String implementationFqName, String classTemplate, String tagName) {
    VirtualFile copied = myFixture.copyDirectoryToProject("", "");
    PsiDirectory dir = myFixture.getPsiManager().findDirectory(copied);
    XmlFile pluginXml = PluginModuleType.getPluginXml(myFixture.getModule());

    NewServiceActionBase.ServiceCreator creator = new NewServiceActionBase.ServiceCreator(dir, null, null, classTemplate, tagName);
    boolean created = creator.createOnlyImplementation(implementationFqName, pluginXml);
    assertTrue(created);

    PsiClass[] createdClasses = creator.getCreatedClasses();
    assertNotNull(createdClasses);
    assertSize(1, createdClasses);

    PsiClass createdImplementation = createdClasses[0];
    assertEquals(implementationFqName.substring(implementationFqName.lastIndexOf(".") + 1), createdImplementation.getName());

    DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(getProject()).getFileElement(pluginXml, IdeaPlugin.class);
    assertNotNull(fileElement);
    IdeaPlugin ideaPlugin = fileElement.getRootElement();
    List<Extensions> extensionsList = ideaPlugin.getExtensions();
    assertNotNull(extensionsList);
    assertEquals(1, extensionsList.size());

    XmlTag extensions = extensionsList.get(0).getXmlTag();
    assertNotNull(extensions);
    XmlTag[] extensionTags = extensions.getSubTags();
    assertNotNull(extensionTags);
    assertSize(1, extensionTags);

    XmlTag serviceTag = extensionTags[0];
    assertEquals(tagName, serviceTag.getName());
    assertNull(serviceTag.getAttributeValue("serviceInterface"));
    assertEquals(implementationFqName, serviceTag.getAttributeValue("serviceImplementation"));
  }
}
