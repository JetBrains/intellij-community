// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GroovyValidGroupNameTest {
  @Test
  public void testGroupNamesIsSame() {
    List<InspectionProfileEntry> tools = InspectionTestUtil.instantiateTools(List.of(UnusedDefInspection.class,
                                                                                     UnnecessaryLocalVariableInspection.class));
    Set<String> result = tools.stream().map(it -> it.getGroupDisplayName()).collect(Collectors.toSet());
    assertEquals(1, result.size());
  }
}
