/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ArrayUtil;

/**
 * @author Dmitry Avdeev
 */
public class LightFileTemplatesTest extends LightPlatformTestCase {

  public static final String TEST_TEMPLATE_TXT = "testTemplate.txt";

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public LightFileTemplatesTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testSchemas() throws Exception {
    assertEquals(FileTemplatesScheme.DEFAULT, myTemplateManager.getCurrentScheme());

    FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
    assertEquals("hi there", template.getText());
    template.setText("good bye");
    assertEquals("good bye", myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());

    myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
    assertEquals("hi there", myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());

    myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
    assertEquals("good bye", myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());
  }

  public void testDefaultProject() throws Exception {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    assertNull(FileTemplateManager.getInstance(defaultProject).getProjectScheme());
  }

  public void testConfigurable() throws Exception {
    AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(getProject());
    try {
      configurable.createComponent();
      configurable.reset();
    }
    finally {
      configurable.disposeUIResources();
    }
  }

  public void testSaveFileAsTemplate() throws Exception {
    AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(getProject());
    try {
      configurable.createComponent();
      configurable.reset();
      FileTemplate template = configurable.createNewTemplate("foo", "bar", "hey");
      assertTrue(configurable.isModified());
      FileTemplate[] templates = configurable.getTabs()[0].getTemplates();
      assertTrue(ArrayUtil.contains(template, templates));
      configurable.changeScheme(myTemplateManager.getProjectScheme());
      assertTrue(configurable.isModified());
//      assertEquals(templates.length, configurable.getTabs()[0].getTemplates().length);
//      assertTrue(ArrayUtil.contains(template, configurable.getTabs()[0].getTemplates()));
    }
    finally {
      configurable.disposeUIResources();
    }
  }

  private FileTemplateManagerImpl myTemplateManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTemplateManager = FileTemplateManagerImpl.getInstanceImpl(getProject());
    FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
    ((BundledFileTemplate)template).revertToDefaults();
  }

  @Override
  public void tearDown() throws Exception {
    myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
    super.tearDown();
  }
}
