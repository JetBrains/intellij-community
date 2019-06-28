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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass(
      "package com.google.common.collect;\n" +
      "\n" +
      "import java.io.Serializable;\n" +
      "import java.util.AbstractCollection;\n" +
      "\n" +
      "public abstract class ImmutableCollection<E> extends AbstractCollection<E> implements Serializable {\n" +
      "  \n" +
      "}");
    myFixture.addClass(
      "package com.google.common.collect;\n" +
      "\n" +
      "import java.util.Set;\n" +
      "\n" +
      "public abstract class ImmutableSet<E> extends ImmutableCollection<E> implements Set<E> {\n" +
      "  public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E... others) {\n" +
      "    return null;\n" +
      "  }\n" +
      "}");
    myFixture.addClass(
      "package com.google.common.collect;\n" +
      "import java.util.List;\n" +
      "\n" +
      "public abstract class ImmutableList<E> extends ImmutableCollection<E> implements List<E> {\n" +
      "  public static <E> ImmutableList<E> of(E e1, E e2) {\n" +
      "    return null;\n" +
      "  }\n" +
      "}");
  }

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

  public void testFieldAssignment() {
    doTest();
  }

  public void testGenericMethod() {
    assertIntentionNotAvailable();
  }

  public void testGenericMethodWithKnownType() {
    doTest();
  }

  public void testNonTrivialQualifier() {
    doTest();
  }

  public void testVolatileField() {
    doTest();
  }

  public void testImmutableListAssignment() {
    assertIntentionNotAvailable();
  }

  public void testImmutableSetVarArgArray() {
    doTest();
  }

  public void testImmutableSetVarArgTernary() {
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
