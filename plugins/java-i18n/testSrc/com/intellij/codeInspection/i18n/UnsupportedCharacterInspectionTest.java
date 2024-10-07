// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.UnsupportedCharacterInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UnsupportedCharacterInspectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnsupportedCharacterInspection());
    myFixture.addClass("""
      package java.util;
      public class ResourceBundle {
        public static ResourceBundle getBundle(String baseName) { return null; }
        public String getString(String key) { return null; }
      }
    """);

  }

  public void testJava8WithConversion() throws IOException {
    javaVersion(LanguageLevel.JDK_1_8);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString(<weak_warning descr="Unsupported characters for the charset 'ISO-8859-1'">"<caret>key1"</weak_warning>);
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + ☕");
    fileEncoding(props, StandardCharsets.UTF_8);
    propertiesEncoding(props, null);

    checkHighlighting(javaFile);
    applyFix(javaFile, "Convert to escape sequences");
    checkFile(props, "key1=Java + \\u2615");
  }

  public void testJava8PlusConstantWithConversion() throws IOException {
    javaVersion(LanguageLevel.JDK_1_8);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        private static final String KEY = <weak_warning descr="Unsupported characters for the charset 'ISO-8859-1'">"<caret>key1"</weak_warning>;
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString(KEY);
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + ☕");
    fileEncoding(props, StandardCharsets.UTF_8);
    propertiesEncoding(props, null);

    checkHighlighting(javaFile);
    applyFix(javaFile, "Convert to escape sequences");
    checkFile(props, "key1=Java + \\u2615");
  }

  public void testJava9NoConversionNeeded() {
    javaVersion(LanguageLevel.JDK_1_9);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString("<caret>key1");
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + ☕");
    fileEncoding(props, StandardCharsets.UTF_8);
    propertiesEncoding(props, null);

    checkHighlighting(javaFile);
  }

  public void testJava8WithCustomizedEncoding() {
    javaVersion(LanguageLevel.JDK_1_8);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString("<caret>key1");
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + ☕");
    fileEncoding(props, StandardCharsets.US_ASCII);
    propertiesEncoding(props, null);

    checkHighlighting(javaFile);
  }

  public void testJava8WithCustomizedEncoding2() {
    javaVersion(LanguageLevel.JDK_1_8);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString("<caret>key1");
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + ☕");
    fileEncoding(props, StandardCharsets.UTF_8);
    propertiesEncoding(props, StandardCharsets.UTF_8);

    checkHighlighting(javaFile);
  }

  public void testJava8NoConversionNeeded() {
    javaVersion(LanguageLevel.JDK_1_8);

    PsiFile javaFile = addClass("Test.java", """
      import java.util.*;
      public final class Test {
        public static void main(String[] args) {
          String value = ResourceBundle.getBundle("test").getString("<caret>key1");
        }
      }
      """);

    PsiFile props = addFile("test.properties", "key1=Java + Coffee");
    fileEncoding(props, StandardCharsets.UTF_8);
    propertiesEncoding(props, null);

    checkHighlighting(javaFile);
  }

  private void javaVersion(LanguageLevel jdk) {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(jdk);
  }

  private static void fileEncoding(PsiFile file, Charset charset) {
    EncodingManager.getInstance().setEncoding(file.getVirtualFile(), charset);
  }

  private static void propertiesEncoding(PsiFile file, Charset charset) {
    EncodingManager.getInstance().setDefaultCharsetForPropertiesFiles(file.getVirtualFile(), charset);
  }

  @SuppressWarnings("SameParameterValue")
  private PsiFile addClass(String fileName, @Language("JAVA") String fileContent) {
    return myFixture.configureByText(fileName, fileContent);
  }

  @SuppressWarnings("SameParameterValue")
  private PsiFile addFile(String fileName, String fileContent) {
    return myFixture.configureByText(fileName, fileContent);
  }

  private void checkHighlighting(PsiFile file) {
    myFixture.testHighlighting(true, false, true, file.getVirtualFile());
  }

  @SuppressWarnings("SameParameterValue")
  private void applyFix(PsiFile file, String fixName) {
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.launchAction(myFixture.findSingleIntention(fixName));
  }

  @SuppressWarnings("SameParameterValue")
  private static void checkFile(PsiFile file, String expectedContent) throws IOException {
    assertEquals(expectedContent, file.getText());
  }
}
