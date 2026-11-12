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
package org.jetbrains.plugins.groovy.intentions.strings;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.GrIntentionFixture;
import org.jetbrains.plugins.groovy.intentions.conversions.strings.RemoveUnnecessaryEscapeCharactersIntention;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class RemoveUnnecessaryEscapedCharactersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, RemoveUnnecessaryEscapeCharactersIntention.HINT, null);

  @Test
  public void testSimple() {
    grIntention.get().doTextTest("""
                 print 'a<caret>b\\"c'
                 """, """
                 print 'a<caret>b"c'
                 """);
  }

  @Test
  public void testAntiSimple() {
    grIntention.get().doAntiTest("""
                  print 'a<caret>b\\'c'""");
  }

  @Test
  public void testGString() {
    grIntention.get().doTextTest("""
                 html ""\"
                 <p>D\\na<caret>ta: ${new Date().format("dd-MM-yyyy hh:mm")}<br/>
                 Totem: $kiosk.name
                  ""\"
                 """, """
                 html ""\"
                 <p>D
                 ata: ${new Date().format("dd-MM-yyyy hh:mm")}<br/>
                 Totem: $kiosk.name
                  ""\"
                 """);
  }
}
