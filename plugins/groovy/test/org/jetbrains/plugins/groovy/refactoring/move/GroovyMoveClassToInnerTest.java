// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GroovyMoveClassToInnerTest extends GroovyMoveTestBase {
  private String[] myConflicts = null;

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClassToInner/";
  }

  public void testContextChange1() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testContextChange2() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testInnerImport() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testInsertInnerClassImport() {
    final GroovyCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(GroovyCodeStyleSettings.class);
    final boolean oldValue = settings.INSERT_INNER_CLASS_IMPORTS;
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    try {
      doTest("pack2.A", "pack1.Class1");
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldValue;
    }
  }

  public void testSimultaneousMove() {
    final GroovyCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCustomSettings(GroovyCodeStyleSettings.class);
    final boolean oldValue = settings.INSERT_INNER_CLASS_IMPORTS;
    settings.INSERT_INNER_CLASS_IMPORTS = false;
    try {
      doTest("pack2.A", "pack1.Class1", "pack0.Class0");
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldValue;
    }
  }

  public void testMoveMultiple1() {
    doTest("pack2.A", "pack1.Class1", "pack1.Class2");
  }

  public void testRefToInner() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testRefToConstructor() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testSecondaryClass() {
    doTest("pack1.User", "pack1.Class2");
  }

  public void testStringsAndComments() {
    doTest("pack1.A", "pack1.Class1");
  }

  public void testStringsAndComments2() {
    doTest("pack1.A", "pack1.Class1");
  }

  public void testNonJava() {
    doTest("pack1.A", "pack1.Class1");
  }

  public void testLocallyUsedPackageLocalToPublicInterface() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testPackageLocalClass() {
    doTest("pack2.A", "pack1.Class1");
  }

  public void testMoveIntoPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from class <b><code>pack1.Class2</code></b>");
  }

  public void testMoveOfPackageLocalClass() {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from class <b><code>pack1.Class2</code></b>");
  }

  public void testMoveIntoPrivateInnerClass() {
    doTestConflicts("pack1.Class1", "pack1.A.PrivateInner", "Class <b><code>pack1.Class1</code></b> will no longer be accessible from class <b><code>pack1.Class2</code></b>");
  }

  public void testMoveWithPackageLocalMember() {
    doTestConflicts("pack1.Class1", "pack2.A", "Method <b><code>Class1.doStuff()</code></b> will no longer be accessible from method <b><code>Class2.test()</code></b>");
  }

  public void testDuplicateInner() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack2.A</code></b> already contains an inner class named <b><code>Class1</code></b>");
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) {
    try {
      myConflicts = expectedConflicts;
      doTest(targetClassName, className);
    }
    finally {
      myConflicts = null;
    }
  }

  @Override
  void perform(String newPackageName, String[] names) {
    final PsiClass[] classes = new PsiClass[names.length];
    for (int i = 0; i < classes.length; i++) {
      String className = names[i];
      classes[i] = myFixture.findClass(className);
      assertNotNull("Class $className not found", classes[i]);
    }

    PsiClass targetClass = myFixture.findClass(newPackageName);
    assertNotNull(targetClass);

    MoveClassToInnerProcessor processor = new MoveClassToInnerProcessor(myFixture.getProject(), classes, targetClass, true, true, null);
    if (myConflicts != null) {
      UsageInfo[] usages = processor.findUsages();
      MultiMap<PsiElement, String> conflicts = processor.getConflicts(usages);
      assertSameElements(conflicts.values(), myConflicts);
    }
    else {
      processor.run();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
    }
  }
}
