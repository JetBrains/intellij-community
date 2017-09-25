/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class PsiErrorElementUtilTest extends LightPlatformCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testNoErrors() {
    PsiFile file = myFixture.configureByText(StdFileTypes.XML, "<hello></hello>");
    Assert.assertFalse(PsiErrorElementUtil.hasErrors(getProject(), file.getVirtualFile()));
  }

  public void testErrors() {
    PsiFile file = myFixture.configureByText(StdFileTypes.XML, "<hello></hello");
    Assert.assertTrue(PsiErrorElementUtil.hasErrors(getProject(), file.getVirtualFile()));
  }
}
