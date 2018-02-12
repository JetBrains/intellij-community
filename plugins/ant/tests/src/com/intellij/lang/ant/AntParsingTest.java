/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang.ant;

import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lang.xml.XmlASTFactory;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.xml.StartTagEndTokenProvider;
import com.intellij.testFramework.ParsingTestCase;

public class AntParsingTest extends ParsingTestCase {

  public AntParsingTest() {
    super("", "ant", new XMLParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, new XmlASTFactory());
    registerExtensionPoint(new ExtensionPointName<>("com.intellij.xml.startTagEndToken"),
                           StartTagEndTokenProvider.class);
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi";
  }

  public void testSingleProject() {
    doTest(true);
  }

  public void testProjectWithDefaultTarget() {
    doTest(true);
  }

  public void testProjectWithDescriptions() {
    doTest(true);
  }

  public void testProjectWithDependentTargets() {
    doTest(true);
  }

  public void testSimpleProperties() {
    doTest(true);
  }

  public void testPropertyLocation() {
    doTest(true);
  }

  public void testPropertyFile() {
    doTest(true);
  }

  public void testSimpleAntCall() {
    doTest(true);
  }

  public void testAntCallNestedTargets() {
    doTest(true);
  }

  public void testAntComments() {
    doTest(true);
  }

  public void testPrologEpilogue() {
    doTest(true);
  }

  public void testMacroDef() {
    doTest(true);
  }

  public void testPresetDef() {
    doTest(true);
  }

  public void testForwardMacroDef() {
    doTest(true);
  }

  public void testSequentialParallel() {
    doTest(true);
  }

  public void testAvailable() {
    doTest(true);
  }

  public void testChecksum() {
    doTest(true);
  }

  public void testChecksum1() {
    doTest(true);
  }

  public void testCondition() {
    doTest(true);
  }

  public void testUptodate() {
    doTest(true);
  }

  public void testDirname() throws Exception {
    doTest(SystemInfo.isWindows ? "_w" : "_u");
  }

  public void testBasename() {
    doTest(true);
  }

  public void testLoadFile() {
    doTest(true);
  }

  public void testTempFile() {
    doTest(true);
  }

  public void testLength() {
    doTest(true);
  }

  public void testPathConvert() {
    doTest(true);
  }

  public void testWhichResource() {
    doTest(true);
  }

  public void testP4Counter() {
    doTest(true);
  }

  public void testJarLibResolve() {
    doTest(true);
  }

  public void testAntTask() {
    doTest(true);
  }

  public void testJava() {
    doTest(true);
  }

   public void testBuildNumber() {
    doTest(true);
  }
}