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
package com.intellij.codeInspection;

import com.intellij.codeInspection.capitalization.TitleCapitalizationInspection;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nls;

public class CapitalizationTest extends TestCase {

  public void testCapitalization() {
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Fix SQL issues", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Fix I18n issues", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Fix C issues", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("@charset is invalid", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Add 'this' qualifier", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Add    'this'    qualifier", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Please select the configuration file (usually named IntelliLang.xml) to import.", Nls.Capitalization.Sentence));
    assertFalse(TitleCapitalizationInspection.isCapitalizationSatisfied("Foo Bar", Nls.Capitalization.Sentence));
    assertTrue(TitleCapitalizationInspection.isCapitalizationSatisfied("Foo", Nls.Capitalization.Sentence));
  }
}
