// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public void testMapOf() { doTest("new HashMap<>()"); }
  public void testMapOfEntries() { doTest("new HashMap<>()"); }
  public void testMapOfEntriesNested() { doTest("new HashMap<>()"); }
  public void testListOf() { doTest("new ArrayList<>()"); }
  public void testLambdaExpr() { doTest("new HashMap<>()"); }
  public void testSingletonMap() { doTest("new HashMap<>()"); }
  public void testSingletonList() { doTest("new ArrayList<>()"); }
  public void testEmpty() { doTest("new ArrayList<>()"); }
  public void testDeclaration() { doTest("new HashMap<>()"); }
  public void testAssignment() { doTest("new HashMap<>()"); }
  public void testSwitchExpression() { doTest("new ArrayList<>()"); }
  public void testAssignmentInSwitchExpression() { doTest("new ArrayList<>()"); }
  public void testMapOfEntriesTernary() { assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  public void testMapOfEntriesArrayAccess() { assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  public void testVarArgCall() { doTest("new HashMap<>()"); }
  public void testFieldAssignment() { doTest("new HashSet<>()"); }
  public void testGenericMethod() { assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  public void testGenericMethodWithKnownType() { doTest("new HashSet<>()"); }
  public void testNonTrivialQualifier() { doTest("new HashMap<>()"); }
  public void testVolatileField() { doTest("new HashMap<>()"); }
  public void testImmutableListAssignment() { assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  public void testImmutableSetVarArgArray() { doTest("new HashSet<>()"); }
  public void testImmutableSetVarArgTernary() { doTest("new HashSet<>()"); }
  public void testInsideTernary() { doTest("new ArrayList<>()"); }

  @Override
  protected void doTest(@NotNull String expression) {
    super.doTest(IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.intention.name", expression));
  }

  @Override
  protected String getRelativePath() {
    return "collections/to_mutable_collection";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}