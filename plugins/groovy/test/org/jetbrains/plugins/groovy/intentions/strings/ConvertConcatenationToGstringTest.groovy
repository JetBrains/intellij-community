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

package org.jetbrains.plugins.groovy.intentions.strings;


import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
public class ConvertConcatenationToGstringTest extends GrIntentionTestCase {
  ConvertConcatenationToGstringTest() {
    super("Convert to GString")
  }

  @NotNull
  final LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel;
      final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  }
  
  final String basePath = TestUtils.testDataPath + 'intentions/convertConcatenationToGstring/'

  public void testSimpleCase() {
    doTest(true);
  }

  public void testVeryComplicatedCase() {
    doTest(true);
  }

  public void testQuotes() {
    doTest(true);
  }

  public void testQuotes2() {
    doTest(true);
  }

  public void testQuotesInMultilineString() {
    doTest(true);
  }

  public void testDot() {
    doTest(true);
  }
}
