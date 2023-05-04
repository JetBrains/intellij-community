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
    doTest("danah boyd <danah@datasociety.net>",
           "danah", "boyd", "DB",
           "danah boyd", "danah@datasociety.net");
    doTest("geohot <geohot@gmail.com>",
           "geohot", "geohot", "G",
           "geohot", "geohot@gmail.com");
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
           "vasya", "vasya", "V",
           "vasya", "vasya");
    doTest("Vasya  Pupkin",
           "Vasya", "Pupkin", "VP",
           "Vasya Pupkin", "Vasya Pupkin");
    doTest("Catherine Zeta-Jones",
           "Catherine", "Zeta-Jones", "CZJ",
           "Catherine Zeta-Jones", "Catherine Zeta-Jones");
    doTest("vasya-pupkin",
           "vasya-pupkin", "vasya-pupkin", "VP",
           "vasya-pupkin", "vasya-pupkin");
    doTest("vasya.pupkin",
           "vasya.pupkin", "vasya.pupkin", "VP",
           "vasya.pupkin", "vasya.pupkin");
    doTest("<asdasd@localhost> Vasya Pupkin",
           "Vasya", "Pupkin", "VP",
           "Vasya Pupkin", "asdasd@localhost");
    doTest("<vasya.pupkin@localhost>",
           "Vasya", "Pupkin", "VP",
           "vasya.pupkin@localhost", "vasya.pupkin@localhost");
    doTest("pupkin <vasya@localhost>",
           "pupkin", "pupkin", "P",
           "pupkin", "vasya@localhost");
    doTest("@localhost",
           "@localhost", "@localhost", "@",
           "@localhost", "@localhost");
    doTest("vasya <email>",
           "vasya", "<email>", "VE",
           "vasya <email>", "vasya <email>");
    doTest("vasya <.email.>",
           "vasya", "<.email.>", "VE",
           "vasya <.email.>", "vasya <.email.>");
    doTest("Vasya-TEST@localhost",
           "Vasya", "TEST", "VT",
           "Vasya-TEST@localhost", "Vasya-TEST@localhost");
  }

  private static void doTest(@NotNull String input,
                             @NotNull String firstName,
                             @NotNull String lastName,
                             @NotNull String initials,
                             @NotNull String fullName,
                             @NotNull String email) {
    assertEquals(input, firstName, shorten(input, FIRSTNAME));
    assertEquals(input, lastName, shorten(input, LASTNAME));
    assertEquals(input, initials, shorten(input, INITIALS));
    assertEquals(input, fullName, shorten(input, NONE));
    assertEquals(input, email, shorten(input, EMAIL));
  }
}
