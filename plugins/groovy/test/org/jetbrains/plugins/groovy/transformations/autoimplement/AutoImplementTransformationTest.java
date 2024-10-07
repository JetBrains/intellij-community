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
package org.jetbrains.plugins.groovy.transformations.autoimplement;

import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class AutoImplementTransformationTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package groovy.transform; public @interface AutoImplement {}");
    myFixture.addClass("public abstract class StringList implements List<String> {}");
  }

  public void testHighlighting() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.AutoImplement

      @AutoImplement
      class SomeClass implements Runnable {
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testHighlightingGenerics() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.AutoImplement

      @AutoImplement
      class SomeClass implements List<Integer> {
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testHighlightingGenericsInherited() {
    myFixture.configureByText("_.groovy", """
      import groovy.transform.AutoImplement

      @AutoImplement
      class SomeClass extends StringList {
      }
      """);
    myFixture.checkHighlighting();
  }
}
