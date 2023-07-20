// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RedundantExplicitChronoFieldInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/redundancy/redundant_explicit_chrono_field/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RedundantExplicitChronoFieldInspection());
    myFixture.addClass("""
                         package java.time.temporal;
                         public interface TemporalUnit{}""");
    myFixture.addClass("""
                         package java.time.temporal;
                         public public enum ChronoUnit implements TemporalUnit{NANOS, YEARS, DECADES, FOREVER}""");
    myFixture.addClass("""
                         package java.time.temporal;
                         public interface TemporalField{}""");
    myFixture.addClass("""
                         package java.time.temporal;
                         public enum ChronoField implements TemporalField{NANO_OF_SECOND, NANO_OF_DAY, YEAR, INSTANT_SECONDS}""");
    myFixture.addClass("""
                         package java.time;
                         import java.time.temporal.TemporalField;
                         public final class OffsetDateTime {
                           public int get(TemporalField field) { return 0; }
                           public int getNano() { return 0; }
                           public int getYear() { return 0; }
                         }""");
    myFixture.addClass("""
                         package java.time;
                         import java.time.temporal.TemporalUnit;
                         public final class OffsetTime {
                           public OffsetTime plus(long amountToAdd, TemporalUnit unit) { return new OffsetTime(); }
                         }""");
  }

  public void testPreview() {
    doTest("Replace with 'getNano()' call");
  }

  public void testOffsetDateTimeConvert() {
    doTest(null);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest(@Nullable String quickFixName) {
    myFixture.configureByFile(getTestDataPath() + "before" + getTestName(false) + ".java");
    if (quickFixName != null) {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(quickFixName));
    }
    else {
      myFixture.launchAction(myFixture.findSingleIntention(InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                                                                     InspectionGadgetsBundle.message(
                                                                                       "inspection.explicit.chrono.field.display.name"))));
    }
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}