// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.ExtensionPoints;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.List;

import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.*;

@TestDataPath("$CONTENT_ROOT/testData/util/extensionLocator")
public class ExtensionLocatorTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "util/extensionLocator";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testByExtensionPoint() {
    VirtualFile virtualFile = myFixture.copyFileToProject("pluginXml_locateByEp.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    XmlFile xmlFile = assertInstanceOf(psiFile, XmlFile.class);
    IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(xmlFile);
    List<ExtensionPoints> epGroups = ideaPlugin.getExtensionPoints();
    assertSize(1, epGroups);
    List<ExtensionPoint> extensionPoints = epGroups.get(0).getExtensionPoints();
    assertSize(2, extensionPoints);

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
  }

  public void testByPsiClass() {
    myFixture.copyFileToProject("pluginXml_locateByPsiClass.xml");
    myFixture.copyFileToProject("SomeClass.java");

    PsiClass arrayListPsiClass = myFixture.findClass("java.util.ArrayList");
    PsiClass linkedListPsiClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_LINKED_LIST);
    PsiClass myList1PsiClass = myFixture.findClass("SomeClass.MyList1");
    PsiClass myList2PsiClass = myFixture.findClass("SomeClass.MyList2");

    verifyLocator(locateExtensionsByPsiClass(arrayListPsiClass), 2);
    verifyLocator(locateExtensionsByPsiClass(linkedListPsiClass), 1);
    verifyLocator(locateExtensionsByPsiClass(myList1PsiClass), 1);
    verifyLocator(locateExtensionsByPsiClass(myList2PsiClass), 0);
  }


  private void verifyLocator(List<ExtensionCandidate> candidates, int expectedExtensionCount) {
    assertSize(expectedExtensionCount, candidates);

    for (int i = 0; i < expectedExtensionCount; i++) {
      ExtensionCandidate candidate = candidates.get(i);
      assertNotNull(candidate);
      DomElement domElement = DomManager.getDomManager(getProject()).getDomElement(candidate.pointer.getElement());
      assertInstanceOf(domElement, Extension.class);
    }
  }
}
