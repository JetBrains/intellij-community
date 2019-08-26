// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.service;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

@TestDataPath("$CONTENT_ROOT/testData/actions/newService/")
public class ServiceCreatorTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "actions/newService";
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
    PsiJavaCodeReferenceElement element = assertOneElement(elements);
    assertEquals(interfaceFqName, element.getQualifiedName());

    XmlTag serviceTag = findServiceTag(pluginXml);
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

    PsiClass createdImplementation = assertOneElement(createdClasses);
    assertEquals(implementationFqName.substring(implementationFqName.lastIndexOf(".") + 1), createdImplementation.getName());

    XmlTag serviceTag = findServiceTag(pluginXml);
    assertEquals(tagName, serviceTag.getName());
    assertNull(serviceTag.getAttributeValue("serviceInterface"));
    assertEquals(implementationFqName, serviceTag.getAttributeValue("serviceImplementation"));
  }

  @NotNull
  private static XmlTag findServiceTag(XmlFile pluginXml) {
    IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml);
    assertNotNull(ideaPlugin);

    Extensions extensions = assertOneElement(ideaPlugin.getExtensions());
    XmlTag extensionsTag = extensions.getXmlTag();

    XmlTag[] extensionTags = extensionsTag.getSubTags();
    return assertOneElement(extensionTags);
  }
}
