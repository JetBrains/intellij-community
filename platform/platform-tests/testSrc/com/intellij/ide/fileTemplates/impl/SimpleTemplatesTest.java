/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.testFramework.LightPlatformTestCase;

import java.util.Properties;

/**
 * @author Dmitry Avdeev
 */
public class SimpleTemplatesTest extends LightPlatformTestCase {

  public void testConditional() throws Exception {
    CustomFileTemplate template = new CustomFileTemplate("foo", "bar");
    template.setText("#set($flag = \"$!IJ_BASE_PACKAGE\" != \"\")\n" +
                     "<option name=\"MAIN_CLASS_NAME\" value=\"$IJ_BASE_PACKAGE#if($flag).#{end}Main\" />"
    );
    Properties attributes = new Properties();
    attributes.setProperty("IJ_BASE_PACKAGE", "");
    assertEquals("<option name=\"MAIN_CLASS_NAME\" value=\"Main\" />", template.getText(attributes));
    attributes.setProperty("IJ_BASE_PACKAGE", "foo.bar");
    assertEquals("<option name=\"MAIN_CLASS_NAME\" value=\"foo.bar.Main\" />", template.getText(attributes));
  }

  public void testInline() throws Exception {
    CustomFileTemplate template = new CustomFileTemplate("foo", "bar");
    template.setText("$IJ_BASE_PACKAGE.replace(\".\", \"/\")");
    Properties attributes = new Properties();
    attributes.setProperty("IJ_BASE_PACKAGE", "foo.bar");
    assertEquals("foo/bar", template.getText(attributes));
  }
}
