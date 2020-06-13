// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class MakeCallChainIntoCallSequenceTest extends IPPTestCase {
  public void testBuilder() { doTest(); }
  public void testBuilderIntoFinal() { doTest(); }
  public void testBuilderStatic() { doTest(); }
  public void testBuilderStaticUnqualified() { doTest(); }
  public void testBuilderField() { doTest(); }
  public void testBuilderFieldString() { doTest(); }
  public void testBuilderLambda() { doTest(); }
  public void testBuilderLambdaVoid() { doTest(); }
  public void testBuilderReturn() { doTest(); }
  public void testBuilderReturnString() { doTest(); }
  public void testBuilderCompoundAssignment() { doTest(); }
  public void testBuilderInIf() { doTest(); }
  public void testBuilderSwitchRuleExpression() { doTest(); }
  public void testBuilderNewExpression() { doTest(); }
  public void testVarNamedX() { doTest(); }
  public void testTransformation() { assertIntentionNotAvailable();}
  //should be probably possible?
  public void testThisCollapse() { assertIntentionNotAvailable();}

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("make.call.chain.into.call.sequence.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/call_chain";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }
}