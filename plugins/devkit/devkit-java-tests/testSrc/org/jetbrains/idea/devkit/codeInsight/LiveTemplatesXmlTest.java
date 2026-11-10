// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LiveTemplatesXmlTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
    myFixture.addFileToProject("MyBundle.properties", "key1=value1");
    myFixture.enableInspections(new XmlUnresolvedReferenceInspection());
  }

  @Test
  public void testResourceBundleAndKeyReferences() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("liveTemplate.xml",
                              "<templateSet>" +
                              "<template resource-bundle=\"MyBundle\"/>" +
                              "<template resource-bundle=\"<error descr=\"Cannot resolve property bundle\">INVALID_VALUE</error>\"/>" +
                              "<template resource-bundle=\"MyBundle\" key=\"key1\"/>" +
                              "<template resource-bundle=\"MyBundle\" key=\"<error descr=\"Cannot resolve property key\">INVALID_VALUE</error>\"/>" +
                              "<template key=\"<error descr=\"Cannot resolve property key\">key1</error>\"/>" +
                              "</templateSet>");
    myFixture.testHighlighting();
  }
}