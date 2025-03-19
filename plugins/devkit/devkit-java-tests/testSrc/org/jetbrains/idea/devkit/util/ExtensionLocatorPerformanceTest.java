// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.execution.console.CustomizableConsoleFoldingBean;
import com.intellij.psi.PsiClass;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.locateExtensionsByPsiClass;

@SkipSlowTestLocally
public class ExtensionLocatorPerformanceTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Attribute.class));
    moduleBuilder.addLibrary("platform-lang-impl", PathUtil.getJarPathForClass(CustomizableConsoleFoldingBean.class));
  }

  public void testLocatingByPsiClass() {
    List<String> randomMethodNames = generateRandomMethodNames();
    myFixture.configureByText("plugin.xml", generatePluginXmlText(randomMethodNames));
    PsiClass psiClass = myFixture.addClass(generateJavaClassText(randomMethodNames));

    Benchmark.newBenchmark("Locating extension tag by PsiClass", () -> {
      List<ExtensionCandidate> result = locateExtensionsByPsiClass(psiClass);
      assertSize(1, result);
    }).attempts(1).start();
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
    StringBuilder sb = new StringBuilder()
      .append("<idea-plugin>\n")
      .append("  <id>com.intellij</id>\n")
      .append("  <name>myPlugin</name>\n");

    sb.append("  <extensionPoints>");
    sb.append("    <extensionPoint name=\"stacktrace.fold\"" +
              " beanClass=\"com.intellij.execution.console.CustomizableConsoleFoldingBean\"/>\n");
    sb.append("    <extensionPoint name=\"myEp\" interface=\"java.util.ArrayList\" />\n");
    sb.append("  </extensionPoints>");

    sb.append("<extensions defaultExtensionNs=\"com.intellij\">");
    methodNames.forEach(s -> sb.append("<stacktrace.fold substring=\"at myPkg.MyClass.").append(s).append("(\"/>\n"));
    sb.append("<myEp implementation=\"myPkg.MyClass\"/>"); // the only valid target for locating
    sb.append("</extensions>");

    sb.append("</idea-plugin>");
    return sb.toString();
  }
}
