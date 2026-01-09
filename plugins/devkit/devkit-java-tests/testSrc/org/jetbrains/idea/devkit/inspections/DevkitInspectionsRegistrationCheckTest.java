// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DevkitInspectionsRegistrationCheckTest extends BasePlatformTestCase {

  private static final int EXPECTED_INSPECTIONS_NUMBER = 91;

  /**
   * Inspections that are finished and intentionally disabled.
   */
  private static final List<String> DISABLED_INSPECTIONS =
    Stream.of(
      "SerializableCtor",
      "StatisticsCollectorNotRegistered"
    ).sorted().toList();

  /**
   * Inspections which implementation is in progress
   * or are finished but not battle-tested yet and may require improvements/polishing.
   */
  private static final List<String> WIP_INSPECTIONS =
    Stream.of("ExtensionClassShouldBeFinalAndNonPublic",
              "CanBeDumbAware",
              "CancellationCheckInLoops",
              "ThreadingConcurrency",
              "CallingMethodShouldBeRequiresBlockingContext",
              "PotentialDeadlockInServiceInitialization",
              "ObsoleteDispatchersEdt",
              "PathAnnotationInspection"
    ).sorted().toList();

  public void testNumberOfKnownDevKitInspections() {
    List<LocalInspectionEP> devkitInspections = getDevKitInspections();
    assertEquals(
      """
        Mismatch in total number of DevKit inspections.
        * If you've just added a DevKit inspection, then just increment DevkitInspectionsRegistrationCheckTest#EXPECTED_INSPECTIONS_NUMBER.
        * Otherwise, check classpath in test run configuration (intellij.devkit.plugin.main)""",
      EXPECTED_INSPECTIONS_NUMBER, devkitInspections.size()
    );
  }

    /**
     * Validates all DevKit inspections that are disabled by default match the expected known set.
     */
  public void testKnownDisabledByDefaultInspections() {
    List<LocalInspectionEP> devkitInspections = getDevKitInspections();
    List<LocalInspectionEP> disabledInspections = ContainerUtil.filter(devkitInspections, ep -> !ep.enabledByDefault);
    List<String> disabledInspectionShortNames = new ArrayList<>(ContainerUtil.map(disabledInspections, ep -> ep.getShortName()));
    Collections.sort(disabledInspectionShortNames);

    assertContainsElements("Mismatch in known disabled inspections", disabledInspectionShortNames, DISABLED_INSPECTIONS);

    List<String> allKnownDisabledInspections = new ArrayList<>(ContainerUtil.concat(DISABLED_INSPECTIONS, WIP_INSPECTIONS));
    Collections.sort(allKnownDisabledInspections);

    assertSameElements("Mismatch in known WIP inspections", disabledInspectionShortNames, allKnownDisabledInspections);
  }

  private static @NotNull List<LocalInspectionEP> getDevKitInspections() {
    List<LocalInspectionEP> devkitInspections = ContainerUtil.filter(LocalInspectionEP.LOCAL_INSPECTION.getExtensionList(), ep -> {
      return "DevKit".equals(ep.getPluginDescriptor().getPluginId().getIdString());
    });
    return devkitInspections;
  }
}
