/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.ExtensionPoints;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.LinkedList;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/util/extensionLocator")
public class ExtensionLocatorTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/util/extensionLocator";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(Attribute.class));
    moduleBuilder.addLibrary("jblist", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(LinkedList.class));
  }

  public void testByExtensionPoint() {
    VirtualFile virtualFile = myFixture.copyFileToProject("pluginXml_locateByEp.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertInstanceOf(psiFile, XmlFile.class);

    XmlFile xmlFile = (XmlFile)psiFile;
    DomManager domManager = DomManager.getDomManager(getProject());
    IdeaPlugin ideaPlugin = assertInstanceOf(domManager.getDomElement(xmlFile.getRootTag()), IdeaPlugin.class);
    List<ExtensionPoints> epGroups = ideaPlugin.getExtensionPoints();
    assertSize(1, epGroups);
    List<ExtensionPoint> extensionPoints = epGroups.get(0).getExtensionPoints();
    assertSize(2, extensionPoints);

    ExtensionPoint namedEp = extensionPoints.get(0);
    ExtensionPoint qualifiedNamedEp = extensionPoints.get(1);
    assertTrue(StringUtil.isNotEmpty(namedEp.getName().getStringValue()));
    assertTrue(StringUtil.isNotEmpty(qualifiedNamedEp.getQualifiedName().getStringValue()));

    verifyLocator(ExtensionLocator.byExtensionPoint(namedEp), 2);
    verifyLocator(ExtensionLocator.byExtensionPoint(qualifiedNamedEp), 2);
  }

  public void testByPsiClass() {
    myFixture.copyFileToProject("pluginXml_locateByPsiClass.xml");
    JavaPsiFacade javaPsiFacade = myFixture.getJavaFacade();
    PsiClass arrayListPsiClass = javaPsiFacade.findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject()));
    PsiClass linkedListPsiClass = javaPsiFacade.findClass("java.util.LinkedList", GlobalSearchScope.allScope(getProject()));

    verifyLocator(ExtensionLocator.byPsiClass(arrayListPsiClass), 2);
    verifyLocator(ExtensionLocator.byPsiClass(linkedListPsiClass), 1);
  }


  private void verifyLocator(ExtensionLocator locator, int expectedExtensionCount) {
    List<ExtensionCandidate> candidates = locator.findCandidates();
    assertSize(expectedExtensionCount, candidates);

    for (int i = 0; i < expectedExtensionCount; i++) {
      ExtensionCandidate candidate = candidates.get(i);
      assertNotNull(candidate);
      DomElement domElement = DomManager.getDomManager(getProject()).getDomElement(candidate.pointer.getElement());
      assertInstanceOf(domElement, Extension.class);
    }
  }
}
