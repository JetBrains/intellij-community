// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection;
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind;

import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind.*;


public class GrStringStyleViolationInspectionTest extends LightGroovyTestCase {
  private static final String PLAIN_KEY = "plain";
  private static final String ESCAPE_KEY = "escape";
  private static final String INTERPOLATION_KEY = "interpolation";
  private static final String MULTILINE_KEY = "multiline";

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  private final GrStringStyleViolationInspection inspection = new GrStringStyleViolationInspection();

  public void testPlainStringCorrection() {
    doTest("\"abc\"", "'abc'", Map.of(PLAIN_KEY, SINGLE_QUOTED));
  }

  public void testNoComplaintForCorrectKind() {
    doTest("'abc'", Map.of(PLAIN_KEY, SINGLE_QUOTED));
  }

  public void testCorrectionSlashyString() {
    doTest("'''abc'''", "/abc/", Map.of(PLAIN_KEY, SLASHY));
  }

  public void testMultilineString() {
    doTest("""
                  '''abc
                  cde'''""",
           """
                  /abc
                  cde/""", Map.of(MULTILINE_KEY, SLASHY));
  }

  public void testNoComplaintOnMultilineStringWithDisabledSettings() {
    doTest("""
                  '''abc
                  cde'''
                  """, Map.of(MULTILINE_KEY, UNDEFINED));
  }

  public void testNoComplaintOnMultilineStringWithMatchingSettings() {
    doTest("\n\"\"\"abc\ncde\"\"\"", Map.of(MULTILINE_KEY, TRIPLE_DOUBLE_QUOTED));
  }

  public void testInterpolatedString() {
    doTest("\"abc${1}de\"", "/abc${1}de/", Map.of(INTERPOLATION_KEY, SLASHY));
  }

  public void testNoComplaintOnInterpolatedStringWithDisabledSettings() {
    doTest("\"${1}\"", Map.of(INTERPOLATION_KEY, UNDEFINED));
  }

  public void testNoComplaintOnInterpolatedStringWithMatchingSettings() {
    doTest("\"\"\"${1}\"\"\"", Map.of(INTERPOLATION_KEY, TRIPLE_DOUBLE_QUOTED));
  }

  public void testEscapingMinimization() {
    doTest("\"ab\\\"c\"", "'ab\"c'", Map.of(PLAIN_KEY, DOUBLE_QUOTED, ESCAPE_KEY, SINGLE_QUOTED));
  }

  public void testEscapingMinimization2() {
    doTest("\"ab\\\"'c\"", "$/ab\"'c/$", Map.of(PLAIN_KEY, DOUBLE_QUOTED,
                                       ESCAPE_KEY, SINGLE_QUOTED));
  }

  public void testConsiderSlashesForSlashyStrings() {
    doTest("\"ab//\\\"c\"",
           "$/ab//\"c/$", Map.of(PLAIN_KEY, DOUBLE_QUOTED,
                                        ESCAPE_KEY, SLASHY));
  }

  public void testComplexMinimization() {
    doTest("'$ /$ $$$ //\\n'", Map.of(PLAIN_KEY, DOUBLE_QUOTED,
                                      ESCAPE_KEY, SLASHY));
  }

  public void testConversionToDollarSlashyString() {
    doTest("'abc$de'", "$/abc$$de/$", Map.of(PLAIN_KEY, DOLLAR_SLASHY_QUOTED,
                                             ESCAPE_KEY, UNDEFINED));
  }

  private void doTest(@NotNull String before, Map<String, InspectionStringQuotationKind> map) {
    doTest(before, null, map);
  }

  private void doTest(@NotNull String before, @Nullable String after, Map<String, InspectionStringQuotationKind> map) {
    inspection.setPlainStringQuotation$intellij_groovy_psi(map.getOrDefault(PLAIN_KEY, SINGLE_QUOTED));
    inspection.setEscapedStringQuotation$intellij_groovy_psi(map.getOrDefault(ESCAPE_KEY, UNDEFINED));
    inspection.setInterpolatedStringQuotation$intellij_groovy_psi(map.getOrDefault(INTERPOLATION_KEY, UNDEFINED));
    inspection.setMultilineStringQuotation$intellij_groovy_psi(map.getOrDefault(MULTILINE_KEY, TRIPLE_QUOTED));

    myFixture.enableInspections(inspection);
    myFixture.configureByText("_.groovy", before);
    if (after == null) {
      myFixture.checkHighlighting(true, false, true);
    }
    else {
      List<IntentionAction> availableIntentionList = myFixture.getAvailableIntentions();
      IntentionAction action = ContainerUtil.find(availableIntentionList, it -> it.getFamilyName().startsWith("Convert to") || it.getFamilyName().contains("Change quotes"));
      myFixture.launchAction(action);
      myFixture.checkResult(after);
    }
  }
}
