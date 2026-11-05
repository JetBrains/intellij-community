// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.structuralsearch.StructuralSearchTestFixtureKt.structuralSearchFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class XmlPredefinedConfigurationsTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<StructuralSearchTestFixture> ssrFixture = structuralSearchFixture(projectFixture);

  @Test
  public void testAll() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    final Configuration[] templates = new XmlStructuralSearchProfile().getPredefinedTemplates();
    final Map<String, Configuration> configurationMap = Stream.of(templates).collect(Collectors.toMap(Configuration::getName, x -> x));
    ssr.checkPredefinedConfiguration(configurationMap.remove(SSRBundle.message("predefined.template.li.not.contained.in.ul.or.ol")),
                                     """
                                       <html>
                                         <ul><li>one</li></ul><li>two</li><li>three</li>
                                       </html>""", HtmlFileType.INSTANCE, "<li>two</li>", "<li>three</li>");
    ssr.checkPredefinedConfiguration(configurationMap.remove(SSRBundle.message("predefined.template.xml.tag.without.specific.attribute")),
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
