// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
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
    doTest(configurationMap.remove(SSRBundle.message("predefined.template.li.not.contained.in.ul.or.ol")),
           """
             <html>
               <ul><li>one</li></ul><li>two</li><li>three</li>
             </html>""", HtmlFileType.INSTANCE, "<li>two</li>", "<li>three</li>");
    doTest(configurationMap.remove(SSRBundle.message("predefined.template.xml.tag.without.specific.attribute")),
           """
             <one attributeName="1">
             	<two attributeName="value"></two>
             	<three attributeName="value"/>
             	<four/>
             	<five></five>
             </one>
             """, XmlFileType.INSTANCE, "<four/>", "<five></five>");
  }
}
