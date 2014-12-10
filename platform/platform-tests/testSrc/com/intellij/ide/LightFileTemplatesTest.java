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
package com.intellij.ide;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author Dmitry Avdeev
 */
public class LightFileTemplatesTest extends LightPlatformTestCase {

  public static final String TEST_TEMPLATE_TXT = "testTemplate.txt";

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
    assertEquals("hi there", myTemplateManager.getDefaultTemplate(TEST_TEMPLATE_TXT).getText());

    getProject().save();
  }

  private FileTemplateManagerImpl myTemplateManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTemplateManager = FileTemplateManagerImpl.getInstanceImpl(getProject());
  }
}
