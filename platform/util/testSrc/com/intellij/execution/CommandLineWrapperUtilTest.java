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
package com.intellij.execution;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandLineWrapperUtilTest {
  @Test
  public void testManifestWithJarsAndDirectories() throws Exception {
    File tempDirectory = FileUtil.createTempDirectory("dirWithClasses", "suffix");
    try {
      List<String> paths = Arrays.asList(tempDirectory.getAbsolutePath(), tempDirectory.getAbsolutePath() + "/directory with spaces/some.jar");
      File jarFile = CommandLineWrapperUtil.createClasspathJarFile(new Manifest(), paths);
      try (JarInputStream inputStream = new JarInputStream(new FileInputStream(jarFile))) {
        Manifest manifest = inputStream.getManifest();
        String classPath = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        String tempDirectoryUrl = tempDirectory.toURI().toURL().toString();
        assertTrue(tempDirectoryUrl, tempDirectoryUrl.endsWith("/"));
        assertEquals(tempDirectoryUrl + " " + tempDirectoryUrl +"directory%20with%20spaces/some.jar", classPath);
      }
      finally {
        FileUtil.delete(jarFile);
      }
    }
    finally {
      FileUtil.delete(tempDirectory);
    }
  }

  @Test
  public void shouldQuoteSpecialSymbolsInArgsFile() {
    assertEquals("simple_string", CommandLineWrapperUtil.quoteArg("simple_string"));
    assertEquals("\"'\"singlequote", CommandLineWrapperUtil.quoteArg("'singlequote"));
    assertEquals("\"\\\"\"doublequote", CommandLineWrapperUtil.quoteArg("\"doublequote"));
    assertEquals("space\" \"string", CommandLineWrapperUtil.quoteArg("space string"));
    assertEquals("hash\"#\"string", CommandLineWrapperUtil.quoteArg("hash#string"));
    assertEquals("\"\\n\"newline\"\\n\"", CommandLineWrapperUtil.quoteArg("\nnewline\n"));
    assertEquals("caret\"\\r\"return", CommandLineWrapperUtil.quoteArg("caret\rreturn"));
    assertEquals("\"\\t\"ta\"\\t\"b", CommandLineWrapperUtil.quoteArg("\tta\tb"));
    assertEquals("formfeed\"\\f\"", CommandLineWrapperUtil.quoteArg("formfeed\f"));
  }
}