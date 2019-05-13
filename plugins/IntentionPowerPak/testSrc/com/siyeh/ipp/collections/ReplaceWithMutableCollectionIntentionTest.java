// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.collections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @see ReplaceWithMutableCollectionIntention
 */
public class ReplaceWithMutableCollectionIntentionTest extends IPPTestCase {

  public void testMapOf() {
    doTest();
  }

  public void testMapOfEntries() {
    doTest();
  }

  public void testListOf() {
    doTest();
  }

  public void testLambdaExpr() {
    doTest();
  }

  public void testSingletonMap() {
    doTest();
  }

  public void testSingletonList() {
    doTest();
  }

  public void testEmpty() {
    doTest();
  }

  public void testDeclaration() {
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testSwitchExpression() {
    doTest();
  }

  public void testAssignmentInSwitchExpression() {
    doTest();
  }

  public void testMapOfEntriesTernary() {
    assertIntentionNotAvailable();
  }

  public void testMapOfEntriesArrayAccess() {
    assertIntentionNotAvailable();
  }

  public void testVarArgCall() {
    doTest();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.family.name");
  }

  @Override
  protected String getRelativePath() {
    return "collections/to_mutable_collection";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }
}
