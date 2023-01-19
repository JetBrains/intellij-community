// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.ide.fileTemplates.InternalTemplateBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.PathKt;
import org.jdom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
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
      FileTemplate template = configurable.createTemplate("foo", "bar", "hey", false);
      assertTrue(configurable.isModified());
      List<FileTemplate> templates = configurable.getTabs()[0].getTemplates();
      assertTrue(templates.contains(template));
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

    Path tempDir = Files.createTempDirectory(getClass().getSimpleName() + '_' + getTestName(true) + '_');
    try {
      Project project = PlatformTestUtil.loadAndOpenProject(tempDir, getTestRootDisposable());
      assertNotNull(project);
      assertNotNull(FileTemplateManager.getInstance(project).getTemplate("foo.txt"));
    }
    finally {
      NioFiles.deleteRecursively(tempDir);
    }
  }

  public void testSurviveOnProjectReopen() throws Exception {
    Path foo = Files.createTempDirectory("surviveOnProjectReopen");
    Project project = PlatformTestUtil.loadAndOpenProject(foo, getTestRootDisposable());
    Disposer.register(getTestRootDisposable(), () -> PathKt.delete(foo));
    String newText = "good bye";
    try {
      FileTemplateManager manager = FileTemplateManager.getInstance(project);
      manager.setCurrentScheme(manager.getProjectScheme());
      FileTemplate template = manager.getTemplate(TEST_TEMPLATE_TXT);
      assertEquals(HI_THERE, template.getText());
      template.setText(newText);
      assertThat(manager.getTemplate(TEST_TEMPLATE_TXT).getText()).isEqualTo(newText);
      manager.saveAllTemplates();
      PlatformTestUtil.saveProject(project, true);
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
    }

    Project reloaded = PlatformTestUtil.loadAndOpenProject(foo, getTestRootDisposable());
    FileTemplateManager manager = FileTemplateManager.getInstance(reloaded);
    assertThat(manager.getCurrentScheme()).isEqualTo(manager.getProjectScheme());
    //manager.setCurrentScheme(FileTemplatesScheme.DEFAULT);
    //manager.setCurrentScheme(manager.getProjectScheme()); // enforce reloading
    assertThat(manager.getTemplate(TEST_TEMPLATE_TXT).getText()).isEqualTo(newText);
  }

  public void testAddRemoveShared() throws Exception {
    Path tempDir = Files.createTempDirectory(getClass().getSimpleName() + '_' + getTestName(true) + '_');
    try {
      Project project = PlatformTestUtil.loadAndOpenProject(tempDir, getTestRootDisposable());
      assertThat(project).isNotNull();
      FileTemplateManager manager = FileTemplateManager.getInstance(project);
      manager.setCurrentScheme(manager.getProjectScheme());
      manager.saveAllTemplates();

      FileTemplateSettings settings = project.getService(FileTemplateSettings.class);
      FTManager ftManager = settings.getDefaultTemplatesManager();
      Path root = ftManager.getConfigRoot();
      Files.createDirectories(root);
      Path file = root.resolve("Foo.java");
      assertTrue(file.toFile().createNewFile());
      manager.saveAllTemplates();
      assertThat(file).isRegularFile();

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
      assertThat(file).isRegularFile();

      templates.remove(templateBase);
      manager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, templates);
      assertThat(file).doesNotExist();
    }
    finally {
      NioFiles.deleteRecursively(tempDir);
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

    FileTemplateSettings settings = ApplicationManager.getApplication().getService(ExportableFileTemplateSettings.class);
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
    FileTemplateSettings settings = ApplicationManager.getApplication().getService(ExportableFileTemplateSettings.class);
    assertEquals(0, settings.getState().getContentSize());
    template.setLiveTemplateEnabled(false);
    Element state = settings.getState();
    assertNotNull(state);
    template.setLiveTemplateEnabled(true);
    assertEquals(0, settings.getState().getContentSize());
  }

  public void testInternalTemplatePlugin() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    ExtensionPoint<InternalTemplateBean> point = InternalTemplateBean.EP_NAME.getPoint();
    InternalTemplateBean bean = new InternalTemplateBean();
    bean.name = "Unknown";
    point.registerExtension(bean, new DefaultPluginDescriptor("testInternalTemplatePlugin"), getTestRootDisposable());
    try {
      myTemplateManager.getInternalTemplates();
      fail();
    }
    catch (Throwable e) {
      assertThat(e.getMessage()).isEqualTo("Can't find template Unknown");
      assertThat(((PluginException)e.getCause()).getPluginId().getIdString()).isEqualTo("testInternalTemplatePlugin");
    }
  }

  public void _testMultiFile() {
    FileTemplate template = myTemplateManager.addTemplate("foo", "txt");
    CustomFileTemplate child =
      (CustomFileTemplate)myTemplateManager.addTemplate("foo.txt" + FileTemplateBase.TEMPLATE_CHILDREN_SUFFIX + "1", "txt");
    template.setChildren(new FileTemplate[]{child});
    myTemplateManager.saveAllTemplates();
    FTManager ftManager = ProjectManager.getInstance().getDefaultProject().getService(FileTemplateSettings.class).getDefaultTemplatesManager();
    ftManager.getTemplates().clear();
    ftManager.loadCustomizedContent();
    FileTemplateBase loaded = ftManager.getTemplate("foo.txt");
    assertNotNull(loaded);
    assertEquals(1, loaded.getChildren().length);
    FileTemplateBase t = ftManager.getTemplate(child.getQualifiedName());
    assertNotNull(t);
  }

  public void testMultiFileSettings() {
    FileTemplate template = myTemplateManager.getTemplate(TEST_TEMPLATE_TXT);
    CustomFileTemplate child = new CustomFileTemplate("child", "txt");
    child.setFileName("child");
    template.setChildren(new FileTemplate[]{child});
    FileTemplateSettings settings = ApplicationManager.getApplication().getService(ExportableFileTemplateSettings.class);
    Element state = settings.getState();
    assertNotNull(state);
    Element element = state.getChildren().get(0).getChildren().get(0);
    assertEquals("""
                   <template name="testTemplate.txt" reformat="true" live-template-enabled="false" enabled="true">
                     <template name="child.txt" file-name="child" reformat="true" live-template-enabled="false" />
                   </template>""", JDOMUtil.writeElement(element));
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
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
