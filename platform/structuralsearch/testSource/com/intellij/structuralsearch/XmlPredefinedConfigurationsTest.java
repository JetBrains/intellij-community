// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.structuralsearch.plugin.ui.Configuration;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class XmlPredefinedConfigurationsTest extends PredefinedConfigurationsTestCase {

  public void testAll() {
    final Configuration[] templates = new XmlStructuralSearchProfile().getPredefinedTemplates();
    final Map<String, Configuration> configurationMap = Stream.of(templates).collect(Collectors.toMap(Configuration::getName, x -> x));
    doTest(configurationMap.remove("<li> not contained in <ul> or <ol>"),
           "<html>\n" +
           "  <ul><li>one</li></ul><li>two</li><li>three</li>\n" +
           "</html>", HtmlFileType.INSTANCE, "<li>two</li>", "<li>three</li>");
  }
}
