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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

import static com.intellij.pom.java.LanguageLevel.JDK_1_9;

/**
 * @author Bas Leijdekkers
 */
public class DanglingJavadocInspectionTest extends LightInspectionTestCase {

  public void testDanglingJavadoc() {
    doTest();
  }

  public void testPackageInfo() {
    doNamedTest("package-info");
  }

  public void testModuleInfo() {
    ModuleRootModificationUtil.updateModel(myModule, m -> m.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(JDK_1_9));
    doNamedTest("module-info");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new DanglingJavadocInspection();
  }
}