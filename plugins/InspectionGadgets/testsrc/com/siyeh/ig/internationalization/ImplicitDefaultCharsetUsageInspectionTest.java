/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;


/**
 * @author Bas Leijdekkers
 */
public class ImplicitDefaultCharsetUsageInspectionTest extends LightInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitDefaultCharsetUsageInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.io;" +
      "import java.io.File;" +
      "import java.io.Writer;" +
      "public class PrintWriter {" +
      "  public PrintWriter(String fileName, String csn) {}" +
      "  public PrintWriter(String fileName) {}" +
      "  public PrintWriter(Writer writer) {}" +
      "}",
      "package java.util;" +
      "import java.io.*;" +
      "public class Formatter {" +
      "  public Formatter(OutputStream os) {}" +
      "  public Formatter(OutputStream os, String csn) {}" +
      "  public Formatter(OutputStream os, String csn, Locale l) {}" +
      "  public Formatter(PrintStream ps) {}" +
      "}",
      "package java.util;" +
      "import java.io.*;" +
      "public class Scanner {" +
      "    public Scanner(InputStream source) {}" +
      "    public Scanner(InputStream source, String charsetName) {}" +
      "    public Scanner(String source) {}" +
      "}"
    };

  }

  public void testImplicitDefaultCharsetUsage() {
    doTest();
  }
}
