// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.execution.console.CustomizableConsoleFoldingBean;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExtensionLocatorPerformanceTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(Attribute.class));
    moduleBuilder.addLibrary("jblist", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(ArrayList.class));
    moduleBuilder.addLibrary("console", PathUtil.getJarPathForClass(CustomizableConsoleFoldingBean.class));
  }

  public void testLocatingByPsiClass() {
    List<String> randomMethodNames = generateRandomMethodNames();
    myFixture.configureByText("plugin.xml", generatePluginXmlText(randomMethodNames));
    PsiClass psiClass = myFixture.addClass(generateJavaClassText(randomMethodNames));

    PlatformTestUtil.startPerformanceTest("Locating extension tag by PsiClass", 2000, () -> {
      List<ExtensionCandidate> result = ExtensionLocator.byPsiClass(psiClass).findCandidates();
      assertSize(1, result);
    }).attempts(1).assertTiming();
  }


  private static List<String> generateRandomMethodNames() {
    return IntStream.range(0, 1000)
                    .mapToObj(i -> UUID.randomUUID().toString().replace("-", "").toLowerCase())
                    .collect(Collectors.toList());
  }

  private static String generateJavaClassText(List<String> methodNames) {
    StringBuilder sb = new StringBuilder("package myPkg;\n\npublic class MyClass {\n");
    methodNames.forEach(s -> sb.append("  public void ").append(s).append("() {}\n"));
    sb.append("}");
    return sb.toString();
  }

  private static String generatePluginXmlText(List<String> methodNames) {
    StringBuilder sb = new StringBuilder().append("<idea-plugin>\n")
                                          .append("  <id>com.intellij</id>\n")
                                          .append("  <name>myPlugin</name>\n");

    sb.append("  <extensionPoints>");
    sb.append("    <extensionPoint name=\"stacktrace.fold\" beanClass=\"com.intellij.execution.console.CustomizableConsoleFoldingBean\"/>\n");
    sb.append("    <extensionPoint name=\"myEp\" interface=\"java.util.ArrayList\" />\n");
    sb.append("  </extensionPoints>");

    sb.append("<extensions defaultExtensionNs=\"com.intellij\">");
    methodNames.forEach(s -> sb.append("<stacktrace.fold substring=\"at myPkg.MyClass.").append(s).append("(\"/>\n"));
    sb.append("<myEp implementation=\"myPkg.MyClass\"/>"); // the only valid target for locating
    sb.append("</extensions>");

    sb.append("  </extensions>\n</idea-plugin>");
    return sb.toString();
  }
}
