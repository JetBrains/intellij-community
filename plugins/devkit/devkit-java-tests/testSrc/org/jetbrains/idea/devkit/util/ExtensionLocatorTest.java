// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.ExtensionPoints;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.locateExtensionsByExtensionPoint;
import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.locateExtensionsByExtensionPointAndId;
import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.locateExtensionsByPsiClass;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$CONTENT_ROOT/testData/util/extensionLocator")
@TestApplication
public class ExtensionLocatorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "util/extensionLocator");

  @Test
  public void testByExtensionPoint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile virtualFile = myFixture.copyFileToProject("pluginXml_locateByEp.xml");
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = PsiManager.getInstance(myFixture.getProject()).findFile(virtualFile);
      XmlFile xmlFile = assertInstanceOf(XmlFile.class, psiFile);
      IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile);
      List<? extends ExtensionPoints> epGroups = ideaPlugin.getExtensionPoints();
      assertThat(epGroups).hasSize(1);
      List<? extends ExtensionPoint> extensionPoints = epGroups.get(0).getExtensionPoints();
      assertThat(extensionPoints).hasSize(2);

      ExtensionPoint namedEp = extensionPoints.get(0);
      ExtensionPoint qualifiedNamedEp = extensionPoints.get(1);
      assertTrue(StringUtil.isNotEmpty(namedEp.getName().getStringValue()));
      assertTrue(StringUtil.isNotEmpty(qualifiedNamedEp.getQualifiedName().getStringValue()));

      verifyLocator(locateExtensionsByExtensionPoint(namedEp), 2);
      verifyLocator(locateExtensionsByExtensionPoint(qualifiedNamedEp), 2);

      verifyLocator(locateExtensionsByExtensionPointAndId(namedEp, "myNamedExtension1").findCandidates(), 1);
      verifyLocator(locateExtensionsByExtensionPointAndId(namedEp, "myNamedExtension2").findCandidates(), 1);
      verifyLocator(locateExtensionsByExtensionPointAndId(qualifiedNamedEp, "myQualifiedNameExtension1").findCandidates(), 1);
      verifyLocator(locateExtensionsByExtensionPointAndId(qualifiedNamedEp, "myQualifiedNamedExtension2").findCandidates(), 1);
    });
  }

  @Test
  public void testByPsiClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginXml_locateByPsiClass.xml");
    myFixture.copyFileToProject("SomeClass.java");

    ReadAction.runBlocking(() -> {
      PsiClass arrayListPsiClass = myFixture.findClass("java.util.ArrayList");
      PsiClass hashMapPsiClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_HASH_MAP);
      PsiClass myList1PsiClass = myFixture.findClass("SomeClass.MyList1");
      PsiClass myList2PsiClass = myFixture.findClass("SomeClass.MyList2");

      verifyLocator(locateExtensionsByPsiClass(arrayListPsiClass), 2);
      verifyLocator(locateExtensionsByPsiClass(hashMapPsiClass), 1);
      verifyLocator(locateExtensionsByPsiClass(myList1PsiClass), 1);
      verifyLocator(locateExtensionsByPsiClass(myList2PsiClass), 0);
    });
  }

  @Test
  public void testExtensionPointsWithSameShortName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <id>my.plugin</id>
        <extensionPoints>
          <extensionPoint qualifiedName="first.console.folding"/>
          <extensionPoint qualifiedName="second.console.folding"/>
        </extensionPoints>
        <extensions defaultExtensionNs="first">
          <console.folding id="first"/>
        </extensions>
        <extensions defaultExtensionNs="second.console">
          <folding id="second"/>
        </extensions>
      </idea-plugin>
      """);
    ReadAction.runBlocking(() -> {
      var plugin = DescriptorUtil.getIdeaPlugin(assertInstanceOf(XmlFile.class, file));
      var points = assertThat(plugin.getExtensionPoints()).singleElement().actual().getExtensionPoints();
      assertThat(points).hasSize(2);
      var first = assertThat(locateExtensionsByExtensionPoint(points.get(0))).singleElement().actual();
      var second = assertThat(locateExtensionsByExtensionPoint(points.get(1))).singleElement().actual();
      assertEquals("first", first.pointer.getElement().getAttributeValue("id"));
      assertEquals("second", second.pointer.getElement().getAttributeValue("id"));
    });
  }

  @Test
  public void testExtensionPointAcrossFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <id>my.plugin</id>
        <extensionPoints>
          <extensionPoint name="myEp" interface="java.lang.Runnable"/>
        </extensionPoints>
      </idea-plugin>
      """);
    var expectedIds = new HashSet<String>();
    for (int i = 0; i < 24; i++) {
      var xml = new StringBuilder("<idea-plugin><id>my.plugin." + i + "</id><depends>my.plugin</depends>");
      xml.append("<extensions defaultExtensionNs=\"my.plugin\">");
      for (int j = 0; j < 12; j++) {
        var id = i + "-" + j;
        expectedIds.add(id);
        xml.append("<myEp implementation=\"java.lang.Thread\" id=\"").append(id).append("\"/>");
      }
      xml.append("</extensions></idea-plugin>");
      myFixture.addFileToProject("plugin" + i + ".xml", xml.toString());
    }
    ReadAction.runBlocking(() -> {
      var plugin = DescriptorUtil.getIdeaPlugin(assertInstanceOf(XmlFile.class, file));
      var point = assertThat(assertThat(plugin.getExtensionPoints()).singleElement().actual().getExtensionPoints()).singleElement().actual();
      var candidates = locateExtensionsByExtensionPoint(point);
      var ids = new HashSet<String>();
      for (var candidate : candidates) {
        var tag = candidate.pointer.getElement();
        assertNotNull(tag);
        assertTrue(ids.add(tag.getAttributeValue("id")));
      }
      assertEquals(expectedIds, ids);
    });
  }

  @Test
  public void testPackagedNestedClassMatches() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <id>my.plugin</id>
        <extensionPoints>
          <extensionPoint name="myEp" interface="java.lang.Runnable"/>
        </extensionPoints>
        <extensions defaultExtensionNs="my.plugin">
          <myEp implementation="myPkg.Outer$Inner"/>
          <myEp implementation="myPkg.Outer"/>
        </extensions>
      </idea-plugin>
      """);
    var scope = GlobalSearchScope.fileScope(file);
    var implementations = new ArrayList<String>();
    ReadAction.runBlocking(() -> {
      ExtensionLocatorKt.processExtensionDeclarations("myPkg.Outer", myFixture.getProject(), true, scope, (_, tag) -> {
        implementations.add(tag.getAttributeValue("implementation"));
        return true;
      });
      assertIterableEquals(List.of("myPkg.Outer"), implementations);

      implementations.clear();
      ExtensionLocatorKt.processExtensionDeclarations("myPkg.Outer", myFixture.getProject(), false, scope, (_, tag) -> {
        implementations.add(tag.getAttributeValue("implementation"));
        return true;
      });
      assertIterableEquals(List.of("myPkg.Outer$Inner", "myPkg.Outer"), implementations);
    });
  }

  private void verifyLocator(List<ExtensionCandidate> candidates, int expectedExtensionCount) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertThat(candidates).hasSize(expectedExtensionCount);

    for (int i = 0; i < expectedExtensionCount; i++) {
      ExtensionCandidate candidate = candidates.get(i);
      assertNotNull(candidate);
      DomElement domElement = DomManager.getDomManager(myFixture.getProject()).getDomElement(candidate.pointer.getElement());
      assertInstanceOf(Extension.class, domElement);
    }
  }
}
