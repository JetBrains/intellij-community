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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;

@TestApplication
public class GrDocResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void test_resolve_to_property_from_within_class() {
    resolve.get().resolveByText("""
                    class Doc {
                      static String C = "123"
                      /**
                       * {@link #<caret>C}
                       */
                      String f() {}
                    }
                    """, GrField.class);
  }

  @Test
  public void test_resolve_to_method_from_within_class() {
    resolve.get().resolveByText("""
                    class Doc {
                      /**
                       * {@link #<caret>f}
                       */
                      static String C = "123"
                      String f() {}
                    }
                    """, GrMethod.class);
  }

  @Test
  public void test_resolve_to_property_from_class_comment() {
    resolve.get().resolveByText("""
                    /**
                     * {@link #<caret>C}
                     */
                    class Doc {
                      static String C = "123"
                    
                      String f() {}
                    }
                    """, GrField.class);
  }

  @Test
  public void test_resolve_to_method_from_class_comment() {
    resolve.get().resolveByText("""
                    /**
                     * {@link #<caret>f}
                     */
                    class Doc {
                      static String C = "123"
                      String f() {}
                    }
                    """, GrMethod.class);
  }
}
