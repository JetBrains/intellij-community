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

import com.intellij.openapi.vcs.actions.ShortNameType;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.actions.ShortNameType.FIRSTNAME;
import static com.intellij.openapi.vcs.actions.ShortNameType.LASTNAME;

/**
 * @author Konstantin Bulenkov
 */
public class AnnotationShortNameTest extends TestCase {
  public void testShortNames() {
    doTest(FIRSTNAME, "Vasya Pavlovich Pupkin <asdasd@localhost>", "Vasya");
    doTest(LASTNAME, "Vasya Pavlovich Pupkin <asdasd@localhost>", "Pupkin");
    doTest(FIRSTNAME, "Vasya Pavlovich Pupkin", "Vasya");
    doTest(LASTNAME, "Vasya Pavlovich Pupkin", "Pupkin");
    doTest(LASTNAME, "vasya.pupkin@localhost.com", "Pupkin");
    doTest(FIRSTNAME, "vasya.pupkin@localhost.com", "Vasya");
    doTest(LASTNAME, "vasya-pavlovich-pupkin@localhost.com", "Pupkin");
    doTest(FIRSTNAME, "vasya-pavlovich-pupkin@localhost.com", "Vasya");
    doTest(FIRSTNAME, "vasya", "vasya");
    doTest(LASTNAME, "vasya", "vasya");
    doTest(FIRSTNAME, "Vasya  Pupkin", "Vasya");
    doTest(LASTNAME, "Vasya  Pupkin", "Pupkin");
  }

  private static void doTest(@NotNull ShortNameType type, @NotNull String fullName, @NotNull String expected) {
    String actual = ShortNameType.shorten(fullName, type);
    assertEquals("Type: " + type, expected, actual);
  }
}
