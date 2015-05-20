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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public class LightFileTemplatesTest extends LightPlatformTestCase {

  public static final String TEST_TEMPLATE_TXT = "testTemplate.txt";
  public static final String HI_THERE = "hi there";

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public LightFileTemplatesTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testSchemas() throws Exception {
    assertEquals(FileTemplatesScheme.DEFAULT, myTemplateManager.getCurrentScheme());

    FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
    assertEquals(HI_THERE, template.getText());
    String newText = "good bye";
    template.setText(newText);
    assertEquals(newText, myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());

    myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
    assertEquals(HI_THERE, myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());

    myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
    assertEquals(newText, myTemplateManager.getTemplate(TEST_TEMPLATE_TXT).getText());
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

  public void testPreserveCustomTemplates() throws Exception {
    myTemplateManager.addTemplate("foo", "txt");
    myTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(myTemplateManager.getAllTemplates()));
    assertNotNull(myTemplateManager.getTemplate("foo.txt"));

    File foo = PlatformTestCase.createTempDir("foo");
    final Project project = ProjectManager.getInstance().createProject("foo", foo.getPath());;
    try {
      assertNotNull(project);
      assertNotNull(FileTemplateManager.getInstance(project).getTemplate("foo.txt"));
    }
    finally {
      closeProject(project);
    }
  }

  public void testSurviveOnProjectReopen() throws Exception {
    File foo = PlatformTestCase.createTempDir("foo");
    Project reloaded = null;
    final Project project = ProjectManager.getInstance().createProject("foo", foo.getPath());;
    try {
      assertNotNull(project);
      FileTemplateManager manager = FileTemplateManager.getInstance(project);
      manager.setCurrentScheme(manager.getProjectScheme());
      FileTemplate template = manager.getTemplate(TEST_TEMPLATE_TXT);
      assertEquals(HI_THERE, template.getText());
      String newText = "good bye";
      template.setText(newText);
      assertEquals(newText, manager.getTemplate(TEST_TEMPLATE_TXT).getText());

      PlatformTestUtil.saveProject(project);
      closeProject(project);

      reloaded = ProjectManager.getInstance().loadAndOpenProject(foo.getPath());
      assertNotNull(reloaded);
      manager = FileTemplateManager.getInstance(reloaded);
      assertEquals(manager.getProjectScheme(), manager.getCurrentScheme());
      //manager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
      //manager.setCurrentScheme(manager.getProjectScheme()); // enforce reloading
      assertEquals(newText, manager.getTemplate(TEST_TEMPLATE_TXT).getText());
    }
    finally {
      closeProject(project);
      closeProject(reloaded);
    }
  }

  private static void closeProject(final Project project) {
    if (project != null && !project.isDisposed()) {
      ProjectManager.getInstance().closeProject(project);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(project);
        }
      });
    }
  }

  public void testRemoveTemplate() throws Exception {
    FileTemplate[] before = myTemplateManager.getAllTemplates();
    try {
      FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
      myTemplateManager.removeTemplate(template);
      assertNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
      myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
      assertNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
      myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
      assertNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
    }
    finally {
      myTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(before));
      myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
      myTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(before));
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
