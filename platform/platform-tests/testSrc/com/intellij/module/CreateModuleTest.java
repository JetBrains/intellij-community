// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;

public class CreateModuleTest extends HeavyPlatformTestCase {
  private static final String RENAMED_MODULE_NAME = "renamed";
  private ModuleManager myModuleManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModuleManager = ModuleManager.getInstance(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myModuleManager = null;
    super.tearDown();
  }

  public void testCreateModuleWorksForPreviousNameOfRenamedModule() {
    Module originalModule = getModule();
    TestCase.assertEquals(originalModule, UsefulTestCase.assertOneElement(myModuleManager.getModules()));

    String originalName = originalModule.getName();
    renameModule(originalModule);

    Module module = createModule(originalName);
    UsefulTestCase.assertSameElements(myModuleManager.getModules(), originalModule, module);
  }

  private void renameModule(Module module) {
    ModifiableModuleModel model = myModuleManager.getModifiableModel();
    try {
      model.renameModule(module, RENAMED_MODULE_NAME);
    } catch (ModuleWithNameAlreadyExists e) {
      throw new IllegalStateException(e);
    }
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
    module.getModuleFile();
    TestCase.assertEquals(RENAMED_MODULE_NAME, module.getName());
  }
}