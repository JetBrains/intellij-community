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
package org.jetbrains.plugins.groovy.lang.highlighting
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
@SuppressWarnings(["JUnitTestClassNamingConvention"])
public class Groovy16HighlightingTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  final LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel
      final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath("$TestUtils.mockGroovy1_6LibraryName!/")
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES)
      modifiableModel.commit()
    }
  }

  final String basePath = TestUtils.testDataPath + "highlighting/"

  private void doTest(LocalInspectionTool... tools) {
    myFixture.enableInspections(tools)
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy")
  }

  public void testInnerEnum() { doTest() }

  public void testSuperWithNotEnclosingClass() { doTest() }

  public void _testThisWithWrongQualifier() { doTest() }

  public void testImplicitEnumCoercion1_6() { doTest(new GroovyAssignabilityCheckInspection()) }

  public void testSlashyStrings() { doTest() }

  public void testDiamonds() { doTest() }
}