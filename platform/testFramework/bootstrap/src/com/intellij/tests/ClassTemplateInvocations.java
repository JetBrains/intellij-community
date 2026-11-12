// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.List;

/**
 * The class template invocations that a test node is nested in.
 *
 * <p>The name of a test holds these invocations, so the tests of two invocations get different names.
 * {@link org.junit.platform.engine.TestSource} cannot tell the invocations apart: each invocation of a class template
 * has the {@link org.junit.platform.engine.support.descriptor.ClassSource} of the template class itself.
 */
public final class ClassTemplateInvocations {
  /**
   * The same constant as {@code org.junit.jupiter.engine.descriptor.ClassTemplateInvocationTestDescriptor.SEGMENT_TYPE},
   * not referenced directly: that class is {@code @API(INTERNAL)} and lives in an engine this module must not depend on.
   * Keyed on the segment type rather than on an engine id on purpose: another engine can give the same shape.
   */
  public static final String SEGMENT_TYPE = "class-template-invocation";

  private ClassTemplateInvocations() {
  }

  /**
   * The display names of every {@code class-template-invocation} node this one is nested in, each in brackets,
   * ordered root to leaf; {@code ""} when there are none.
   *
   * <p>The node's own last segment is excluded: an invocation node is named by its own display name already.
   *
   * <p>The unique id is authoritative for how many qualifiers there are and in which order; the test plan is
   * consulted only to upgrade the bare segment value ({@code #1}) to the human-readable invocation display name
   * ({@code [1] alpha}). That split is what keeps this defensive: the plan is absent on the paths that report
   * before {@code testPlanExecutionStarted}, and {@link TestPlan#getTestIdentifier} throws for any node the
   * launcher never added, so every level degrades on its own to {@code #N} - unique, if not pretty - instead of
   * losing the qualifier or its position.
   *
   * @param testPlan the plan of the run, or {@code null}
   */
  public static String qualifier(TestIdentifier testIdentifier, TestPlan testPlan) {
    UniqueId uniqueId = testIdentifier.getUniqueIdObject();
    List<UniqueId.Segment> segments = uniqueId.getSegments();
    int count = 0;
    for (int i = 0; i < segments.size() - 1; i++) {  // the last segment names this node, not an ancestor
      if (SEGMENT_TYPE.equals(segments.get(i).getType())) count++;
    }
    if (count == 0) return "";  // the overwhelmingly common case: no plan lookup, no allocation

    String[] names = new String[count];
    UniqueId ancestor = uniqueId;
    for (int i = segments.size() - 2; i >= 0 && count > 0; i--) {
      ancestor = ancestor.removeLastSegment();  // now ends with segments.get(i)
      UniqueId.Segment segment = segments.get(i);
      if (!SEGMENT_TYPE.equals(segment.getType())) continue;
      names[--count] = invocationName(testPlan, ancestor, segment);  // walked leaf to root, stored root to leaf
    }

    StringBuilder result = new StringBuilder();
    for (String name : names) {
      result.append('[').append(name).append(']');
    }
    return result.toString();
  }

  private static String invocationName(TestPlan testPlan, UniqueId uniqueId, UniqueId.Segment segment) {
    if (testPlan != null) {
      try {
        String displayName = testPlan.getTestIdentifier(uniqueId).getDisplayName();
        if (!displayName.isEmpty()) return displayName;
      }
      catch (RuntimeException ignored) {
        // PreconditionViolationException: no such node in this plan. Fall through to the index, always available.
      }
    }
    return segment.getValue();  // "#1"
  }
}
