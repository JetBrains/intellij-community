/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usagesStatistics;

import com.intellij.internal.statistic.analytics.AnalyticsUploader;
import com.intellij.openapi.project.IndexNotReadyException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyticsUploaderTest extends TestCase {
  public void testMessageIgnored() {
    IOException ioe = new IOException("File C://User//Data not found");
    assertTrue(AnalyticsUploader.getDescription(ioe).startsWith("IOEx @ AnalyticsUploaderTest:37 <"));
  }

  public void testRepetitiveFileNames() {
    Throwable t = createExceptionFromDesc(
      "com.intellij.openapi.project.IndexNotReadyException: Please change caller according to com.intellij.openapi.project.IndexNotReadyException documentation\n" +
      "\tat com.intellij.util.indexing.FileBasedIndexImpl.handleDumbMode(FileBasedIndexImpl.java:853)\n" +
      "\tat com.intellij.util.indexing.FileBasedIndexImpl.ensureUpToDate(FileBasedIndexImpl.java:802)\n" +
      "\tat com.intellij.util.indexing.FileBasedIndexImpl.ensureUpToDate(FileBasedIndexImpl.java:786)\n" +
      "\tat com.intellij.psi.stubs.StubIndexImpl.processElements(StubIndexImpl.java:250)\n" +
      "\tat com.intellij.psi.stubs.StubIndexImpl.processElements(StubIndexImpl.java:238)\n" +
      "\tat com.intellij.psi.stubs.StubIndex.process(StubIndex.java:76)\n" +
      "\tat com.intellij.psi.stubs.StubIndex.process(StubIndex.java:95)\n" +
      "\tat com.intellij.psi.stubs.StubIndexImpl.get(StubIndexImpl.java:227)\n" +
      "\tat com.intellij.psi.stubs.StubIndex.getElements(StubIndex.java:144)\n",
      new IndexNotReadyException());
    assertEquals("IndexNotReadyEx @ FileBasedIndexImpl:853 < :802 < :786 < StubIndexImpl:250 < :238 < StubIndex:76 < :95 < StubIndexImpl:227 < StubIndex:144",
                 AnalyticsUploader.getDescription(t));
  }

  public void testAndroidPluginIncluded() {
    Throwable t = createExceptionFromDesc(
      "java.lang.Throwable\n" +
      "\tat com.intellij.openapi.diagnostic.Logger.error(Logger.java:126)\n" +
      "\tat com.intellij.openapi.application.impl.ApplicationImpl.assertReadAccessAllowed(ApplicationImpl.java:976)\n" +
      "\tat com.intellij.psi.impl.source.tree.CompositeElement.textToCharArray(CompositeElement.java:293)\n" +
      "\tat com.intellij.psi.impl.source.tree.CompositeElement.getText(CompositeElement.java:263)\n" +
      "\tat com.intellij.extapi.psi.ASTDelegatePsiElement.getText(:141)\n" +
      "\tat com.intellij.psi.impl.source.tree.CompositeElement.getText(CompositeElement.java:263)\n" +
      "\tat com.android.tools.idea.gradle.parser.GradleGroovyFile.getMethodCallName(GradleGroovyFile.java:337)\n" +
      "\tat com.android.tools.idea.gradle.parser.GradleGroovyFile$2.apply(GradleGroovyFile.java:327)\n" +
      "\tat com.android.tools.idea.gradle.parser.GradleGroovyFile$2.apply(GradleGroovyFile.java:324)\n" +
      "\tat com.google.common.collect.Iterators$7.computeNext(Iterators.java:647)\n",
      null
    );
    assertEquals("Throwable @ Logger:126 < ApplicationImpl:976 < CompositeElement:293 < :263 < U:141 < CompositeElement:263 < GradleGroovyFile:337 < :327 < :324 < Iter>",
                 AnalyticsUploader.getDescription(t));
  }

  public void testJavaLangSkipped() {
    Throwable t = createExceptionFromDesc(
      "java.lang.StringIndexOutOfBoundsException: String index out of range: 10\n" +
      "\tat java.lang.String.substring(String.java:1934)\n" +
      "\tat com.android.tools.idea.ddms.adb.AdbService.getDebugBridge(AdbService.java:83)\n",
      null
    );
    assertEquals("StringIndexOutOfBoundsEx @ . < AdbService:83", AnalyticsUploader.getDescription(t));
  }

  // Copied from RenderErrorPanelTest
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc, @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Arrays.asList(desc.split("\n")).iterator(); // Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    } else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        } else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      } catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        } else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = new ArrayList<StackTraceElement>();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      } else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        if (inner.equals("Native Method")) {
          frames.add(new StackTraceElement(clz, method, null, -2));
        } else if (inner.equals("Unknown Source")) {
          frames.add(new StackTraceElement(clz, method, null, -1));
        } else {
          Matcher innerMatcher = innerPattern.matcher(inner);
          if (!innerMatcher.matches()) {
            fail("Trace parameter list " + inner + " does not match expected pattern");
          } else {
            String file = innerMatcher.group(1);
            int lineNum = Integer.parseInt(innerMatcher.group(2));
            frames.add(new StackTraceElement(clz, method, file, lineNum));
          }
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    assertEquals(desc, getStackTrace(throwable));

    return throwable;
  }

  @NotNull
  private static String getStackTrace(@NotNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    try {
      t.printStackTrace(writer);
      return stringWriter.toString();
    }
    finally {
      writer.close();
    }
  }
}
