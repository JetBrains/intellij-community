/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.highlighting;

import com.intellij.appengine.AppEngineCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;

/**
 * @author nik
 */
@TestDataPath("$CONTENT_ROOT/testData/highlighting/descriptor/")
public class AppEngineDescriptorHighlightingTest extends AppEngineCodeInsightTestCase {
  public void testAppEngineWeb() {
    myCodeInsightFixture.configureByFile("appengine-web.xml");
    checkXmlHighlighting();
  }

  public void testJdoConfig() {
    myCodeInsightFixture.configureByFile("jdoconfig.xml");
    checkXmlHighlighting();
  }

  public void testApplication() {
    myCodeInsightFixture.configureByFile("appengine-application.xml");
    checkXmlHighlighting();
  }

  private void checkXmlHighlighting() {
    myCodeInsightFixture.enableInspections(CheckXmlFileWithXercesValidatorInspection.class);
    myCodeInsightFixture.checkHighlighting();
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "highlighting/descriptor";
  }
}
