// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Assert;
import org.junit.Test;


public class BaseScriptTransformationSupportTest extends GroovyLatestTest implements ResolveTest {
  private void doStubTest(String text, String packageName) {
    getFixture().addFileToProject("script/base.groovy", "package script; abstract class MyBaseScript extends Script {}");
    GroovyFileImpl file = (GroovyFileImpl)getFixture().addFileToProject("Zzz.groovy", text);
    Assert.assertFalse(file.isContentsLoaded());

    PsiClass clazz = getFixture().findClass(packageName == null ? "Zzz" : packageName + ".Zzz");
    Assert.assertTrue(clazz instanceof GroovyScriptClass);
    Assert.assertFalse(file.isContentsLoaded());

    Assert.assertTrue(InheritanceUtil.isInheritor(clazz, "script.MyBaseScript"));
    Assert.assertFalse(file.isContentsLoaded());
  }

  private void doStubTest(String text) {
    doStubTest(text, null);
  }

  @Test
  public void testTopLevel() {
    doStubTest("@groovy.transform.BaseScript script.MyBaseScript hello");
  }

  @Test
  public void testScriptBlockLevel() {
    doStubTest("if (true) @groovy.transform.BaseScript script.MyBaseScript hello");
  }

  @Test
  public void testWithinMethod() {
    doStubTest("""
                 def foo() {
                   @groovy.transform.BaseScript script.MyBaseScript hello \s
                 }
                 """);
  }

  @Test
  public void testOnImport() {
    doStubTest("""
                 @BaseScript(script.MyBaseScript)
                 import groovy.transform.BaseScript
                 """);
  }

  @Test
  public void testOnPackage() {
    doStubTest("""
                 @groovy.transform.BaseScript(script.MyBaseScript)
                 package com.foo
                 """, "com.foo");
  }

  @Test
  public void testNoAEWhenScriptClassHasSameNameAsAPackage() {
    getFixture().addClass("""
                            package root.foo;
                            public abstract class Bar extends groovy.lang.Script {}
                            """);
    getFixture().configureByText(
        "root.groovy", """
               import root.foo.Bar
               @groovy.transform.BaseScript Bar dsl
          """);
    getFixture().checkHighlighting();
  }

  @Test
  public void testResolveToBaseClassGetter() {
    getFixture().addFileToProject("classes.groovy", """
      abstract class BaseClass extends Script {
          int getStuffFromBaseClass() { 42 }
      }
      """);
    resolveTest("""
                  @groovy.transform.BaseScript BaseClass script
                  <caret>stuffFromBaseClass
                  """, GrMethod.class);
  }

  @Test
  public void testCircularInheritance() {
    getFixture().addFileToProject("Util.groovy", """
      abstract class Util extends Script {}
      """);
    getFixture().configureByText("Script.groovy", """
      <error>@groovy.transform.BaseScript</error> Util script
      """);
    getFixture().checkHighlighting();
  }
}
