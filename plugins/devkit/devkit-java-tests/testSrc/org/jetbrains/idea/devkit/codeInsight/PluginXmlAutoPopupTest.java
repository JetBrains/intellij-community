/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixtureKt.javaCompletionAutoPopupFixture;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class PluginXmlAutoPopupTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<JavaCompletionAutoPopupFixture> autoPopupFixture = javaCompletionAutoPopupFixture(projectFixture);
  private LightJavaCodeInsightTestFixture myFixture;
  private JavaCompletionAutoPopupFixture myAutoPopup;

  @BeforeEach
  void setUp() {
    myAutoPopup = autoPopupFixture.get();
    myFixture = myAutoPopup.getFixture();
  }

  @Test
  public void test_autopopup_for_class_references() {
    myFixture.addClass("public class FooFooFooFooFoo { }");
    myFixture.configureByText("plugin.xml", """
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="FooFooFooFooFox" interface="FFFF<caret>"/>
  </extensionPoints>
</idea-plugin>
""");
    myAutoPopup.getTester().typeWithPauses("o");
    assertNotNull(myFixture.getLookup());
    assertSameElements(myFixture.getLookupElementStrings(), List.of("FooFooFooFooFoo"));
  }

  @Test
  public void test_no_autopopup_when_only_word_completion_is_available() {
    myFixture.configureByText("plugin.xml", """
<idea-plugin>
  <extensionPoints>
    <extensionPoint name="<caret>"/>
  </extensionPoints>
</idea-plugin>
""");
    myAutoPopup.getTester().typeWithPauses("e");
    assertNull(myFixture.getLookup());
  }
}
