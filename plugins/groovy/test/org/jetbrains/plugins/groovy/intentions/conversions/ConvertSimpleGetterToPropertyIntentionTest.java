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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

@TestApplication
public class ConvertSimpleGetterToPropertyIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, "Convert to property", TestUtils.getAbsoluteTestDataPath() + "intentions/convert/getterToProperty/");

  @Test
  public void testTyped() { grIntention.get().doTest(true); }

  @Test
  public void testUntyped() { grIntention.get().doTest(true); }

  @Test
  public void testVoid() { grIntention.get().doTest(false); }

  @Test
  public void testPrivate() { grIntention.get().doTest(true); }

  @Test
  public void testStatic() { grIntention.get().doTest(true); }
}
