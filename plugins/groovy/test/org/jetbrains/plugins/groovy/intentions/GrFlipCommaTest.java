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
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.openapi.editor.actions.FlipCommaIntention;

public class GrFlipCommaTest extends GrIntentionTestCase {
  public GrFlipCommaTest() {
    super(new FlipCommaIntention().getFamilyName());
  }

  public void testFlipFirstAndMiddleParameters() {
    doTextTest("def m(String a<caret>, int b, boolean c) {}",
               "def m(int b<caret>, String a, boolean c) {}");
    doTextTest("def m(String a,<caret> int b, boolean c) {}",
               "def m(int b, String a, boolean c) {}");
  }

  public void testFlipMiddleAndLastParameters() {
    doTextTest("def m(String a, int b<caret>, boolean c) {}",
               "def m(String a, boolean c<caret>, int b) {}");
    doTextTest("def m(String a, int b,<caret> boolean c) {}",
               "def m(String a, boolean c, int b) {}");
  }

  public void testFlipFirstAndMiddleListElements() {
    doTextTest("[1<caret>, 2, 3]",
               "[2<caret>, 1, 3]");
    doTextTest("[1,<caret> 2, 3]",
               "[2, 1, 3]");
  }

  public void testFlipMiddleAndLastListElements() {
    doTextTest("[1, 2<caret>, 3]",
               "[1, 3<caret>, 2]");
    doTextTest("[1, 2,<caret> 3]",
               "[1, 3, 2]");
  }
}
