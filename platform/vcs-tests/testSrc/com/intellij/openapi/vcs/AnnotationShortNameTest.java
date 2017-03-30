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
  public void testShortNames() throws Exception {
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
