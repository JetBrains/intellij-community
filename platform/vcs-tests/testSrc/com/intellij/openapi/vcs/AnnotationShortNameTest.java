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
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.actions.ShortNameType.*;

public class AnnotationShortNameTest extends TestCase {
  public void testShortNames() {
    doTest("Vasya Pavlovich Pupkin <asdasd@localhost>",
           "Vasya", "Pupkin", "VPP",
           "Vasya Pavlovich Pupkin", "asdasd@localhost");
    doTest("Vasya Pavlovich Pupkin",
           "Vasya", "Pupkin", "VPP",
           "Vasya Pavlovich Pupkin", "Vasya Pavlovich Pupkin");
    doTest("vasya.pupkin@localhost.com",
           "Vasya", "Pupkin", "VP",
           "vasya.pupkin@localhost.com", "vasya.pupkin@localhost.com");
    doTest("vasya-pavlovich-pupkin@localhost.com",
           "Vasya", "Pupkin", "VPP",
           "vasya-pavlovich-pupkin@localhost.com", "vasya-pavlovich-pupkin@localhost.com");
    doTest("vasya",
           "Vasya", "Vasya", "V",
           "vasya", "vasya");
    doTest("Vasya  Pupkin",
           "Vasya", "Pupkin", "VP",
           "Vasya Pupkin", "Vasya Pupkin");
    doTest("vasya-pupkin",
           "Vasya", "Pupkin", "VP",
           "vasya-pupkin", "vasya-pupkin");
    doTest("vasya.pupkin",
           "Vasya", "Pupkin", "VP",
           "vasya.pupkin", "vasya.pupkin");
    doTest("<asdasd@localhost> Vasya Pupkin",
           "Vasya", "Pupkin", "VP",
           "Vasya Pupkin", "asdasd@localhost");
    doTest("<vasya.pupkin@localhost>",
           "Vasya", "Pupkin", "VP",
           "vasya.pupkin@localhost", "vasya.pupkin@localhost");
    doTest("pupkin <vasya@localhost>",
           "Pupkin", "Pupkin", "P",
           "pupkin", "vasya@localhost");
    doTest("@localhost",
           "@localhost", "@localhost", "@",
           "@localhost", "@localhost");
    doTest("vasya <email>",
           "Vasya", "Email", "VE",
           "vasya <email>", "vasya <email>");
    doTest("vasya <.email.>",
           "Vasya", "Email", "VE",
           "vasya <.email.>", "vasya <.email.>");
  }

  private static void doTest(@NotNull String input,
                             @NotNull String firstName,
                             @NotNull String lastName,
                             @NotNull String initials,
                             @NotNull String fullName,
                             @NotNull String email) {
    assertEquals(firstName, shorten(input, FIRSTNAME));
    assertEquals(lastName, shorten(input, LASTNAME));
    assertEquals(initials, shorten(input, INITIALS));
    assertEquals(fullName, shorten(input, NONE));
    assertEquals(email, shorten(input, EMAIL));
  }
}
