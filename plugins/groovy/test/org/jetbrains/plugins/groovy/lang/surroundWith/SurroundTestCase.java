// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;

public abstract class SurroundTestCase extends LightGroovyTestCase {
  protected void doTest(final Surrounder surrounder) {
    final Iterator<String> iterator = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test").iterator();
    String before = iterator.hasNext() ? iterator.next() : null;
    String after = iterator.hasNext() ? iterator.next() : null;
    doTest(surrounder, before, after);
  }

  protected void doTest(final Surrounder surrounder, String textBefore, String textAfter) {
    myFixture.configureByText("a.groovy", textBefore);
    SurroundWithHandler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    myFixture.checkResult(textAfter);
  }
}
