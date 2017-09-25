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
package com.intellij.openapi.vcs;

import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

@NonNls public class ErrorMessageTest extends TestCase {
  public void test() {

    doTest(1, 1, "One error and one warning found.");
    doTest(0, 1, "No errors and one warning found.");
    doTest(1, 0, "One error and no warnings found.");
    doTest(1, 10, "One error and 10 warnings found.");
    doTest(10, 1, "10 errors and one warning found.");
    doTest(10, 0, "10 errors and no warnings found.");
    doTest(0, 10, "No errors and 10 warnings found.");
  }

  private void doTest(final int errors, final int warnings, final String expected) {
    final String message = VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text", errors, warnings);
    assertTrue(message.indexOf(expected) >= 0);
  }
}
