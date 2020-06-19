/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingOverrideAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest();
  }

  public void testNotAvailable() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testHierarchy2() {
    doTest();
  }
  
  public void testSimpleJava5() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, this::doTest);
  }
  
  public void testNotAvailableMethodInLanguageLevel7() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, this::doTest);
  }
  
  public void testRecordAccessorJava14() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_14_PREVIEW, this::doTest);
  }
  
  public void testRecordAccessorJava15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15_PREVIEW, this::doTest);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MissingOverrideAnnotationInspection();
  }
}
