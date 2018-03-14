// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class LightFileTemplatesTest extends LightPlatformTestCase {
  private static final String TEST_TEMPLATE_TXT = "testTemplate.txt";
  private static final String HI_THERE = "hi there";

  public void testSchemas() {
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

  public void testDefaultProject() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    assertNull(FileTemplateManager.getInstance(defaultProject).getProjectScheme());
  }

  public void testConfigurable() {
    AllFileTemplatesConfigurable configurable = new AllFileTemplatesConfigurable(getProject());
    try {
      configurable.createComponent();
      configurable.reset();
    }
    finally {
      configurable.disposeUIResources();
    }
  }

  public void testSaveFileAsTemplate() {
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

    File foo = FileUtilRt.createTempDirectory("foo", null, false);
    final Project project = ProjectManager.getInstance().createProject("foo", foo.getPath());
    try {
      assertNotNull(project);
      assertNotNull(FileTemplateManager.getInstance(project).getTemplate("foo.txt"));
    }
    finally {
      closeProject(project);
      FileUtilRt.delete(foo);
    }
  }

  public void testSurviveOnProjectReopen() throws Exception {
    File foo = FileUtilRt.createTempDirectory("foo", null, false);
    Project reloaded = null;
    final Project project = ProjectManager.getInstance().createProject("foo", foo.getPath());
    try {
      assertThat(project).isNotNull();
      FileTemplateManager manager = FileTemplateManager.getInstance(project);
      manager.setCurrentScheme(manager.getProjectScheme());
      FileTemplate template = manager.getTemplate(TEST_TEMPLATE_TXT);
      assertEquals(HI_THERE, template.getText());
      String newText = "good bye";
      template.setText(newText);
      assertEquals(newText, manager.getTemplate(TEST_TEMPLATE_TXT).getText());
      manager.saveAllTemplates();
      // closeProject will not save project since it is not opened
      PlatformTestUtil.saveProject(project, true);
      closeProject(project);

      reloaded = ProjectManager.getInstance().loadAndOpenProject(foo.getPath());
      assertNotNull(reloaded);
      manager = FileTemplateManager.getInstance(reloaded);
      assertThat(manager.getCurrentScheme()).isEqualTo(manager.getProjectScheme());
      //manager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
      //manager.setCurrentScheme(manager.getProjectScheme()); // enforce reloading
      assertEquals(newText, manager.getTemplate(TEST_TEMPLATE_TXT).getText());
    }
    finally {
      closeProject(project);
      closeProject(reloaded);
      FileUtilRt.delete(foo);
    }
  }

  public void testAddRemoveShared() throws Exception {
    File foo = FileUtilRt.createTempDirectory("foo", null, false);
    final Project project = ProjectManager.getInstance().createProject("foo", foo.getPath());
    try {
      assertThat(project).isNotNull();
      FileTemplateManager manager = FileTemplateManager.getInstance(project);
      manager.setCurrentScheme(manager.getProjectScheme());
      manager.saveAllTemplates();

      FileTemplateSettings settings = ServiceManager.getService(project, FileTemplateSettings.class);
      FTManager ftManager = settings.getDefaultTemplatesManager();
      File root = ftManager.getConfigRoot(false);
      assertTrue(root.exists());
      File file = new File(root, "Foo.java");
      assertTrue(file.createNewFile());
      manager.saveAllTemplates();
      assertTrue(file.exists());

      /*
      FileTemplate template = manager.addTemplate("Foo", "java");
      // now remove it via "remove template" call
      manager.removeTemplate(template);
      manager.saveAllTemplates();
      assertFalse(file.exists());
      */

      // check "setTemplates" call
      FileTemplateBase templateBase = (FileTemplateBase)manager.addTemplate("Foo", "java");
      List<FileTemplate> templates = new ArrayList<>(ftManager.getAllTemplates(true));
      assertTrue(templates.contains(templateBase));
      ftManager.saveTemplates();
      assertTrue(file.exists());

      templates.remove(templateBase);
      manager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, templates);
      assertFalse(file.exists());
    }
    finally {
      closeProject(project);
      FileUtilRt.delete(foo);
    }
  }

  private static void closeProject(final Project project) {
    if (project != null && !project.isDisposed()) {
      ProjectManager.getInstance().closeProject(project);
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
    }
  }

  public void testRemoveTemplate() {
    FileTemplate[] before = myTemplateManager.getAllTemplates();
    try {
      FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
      myTemplateManager.removeTemplate(template);
      assertNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
      myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
      assertNotNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
      myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
      assertNull(myTemplateManager.getTemplate(TEST_TEMPLATE_TXT));
    }
    finally {
      myTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(before));
      myTemplateManager.setCurrentScheme(myTemplateManager.getProjectScheme());
      myTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, Arrays.asList(before));
    }
  }

  public void testSaveReformatCode() {
    FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
    assertTrue(template.isReformatCode());
    template.setReformatCode(false);

    FileTemplateSettings settings = ServiceManager.getService(ExportableFileTemplateSettings.class);
    Element state = settings.getState();
    assertNotNull(state);
    Element element = state.getChildren().get(0).getChildren().get(0);
    assertEquals("<template name=\"testTemplate.txt\" reformat=\"false\" live-template-enabled=\"false\" enabled=\"true\" />", JDOMUtil
      .writeElement(element));
  }

  public void testDoNotSaveDefaults() {
    assertFalse(((FileTemplateBase)myTemplateManager.getTemplate(TEST_TEMPLATE_TXT)).isLiveTemplateEnabledByDefault());
    FileTemplateBase template = (FileTemplateBase)myTemplateManager.getTemplate("templateWithLiveTemplate.txt");
    assertTrue(template.isLiveTemplateEnabledByDefault());
    FileTemplateSettings settings = ServiceManager.getService(ExportableFileTemplateSettings.class);
    assertEquals(0, settings.getState().getContentSize());
    template.setLiveTemplateEnabled(false);
    Element state = settings.getState();
    assertNotNull(state);
    template.setLiveTemplateEnabled(true);
    assertEquals(0, settings.getState().getContentSize());
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
    try {
      myTemplateManager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
      PropertiesComponent.getInstance().unsetValue("FileTemplates.SelectedTemplate");
    }
    finally {
      super.tearDown();
    }
  }
}
