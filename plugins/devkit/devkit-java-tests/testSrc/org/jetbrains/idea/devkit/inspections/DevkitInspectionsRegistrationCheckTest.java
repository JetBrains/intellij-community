// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

// disabled; enable again when needed

public class DevkitInspectionsRegistrationCheckTest /*extends BasePlatformTestCase*/ {
  private static final int EXPECTED_INSPECTIONS_NUMBER = 94;

  /**
   * Inspections that are finished and intentionally disabled.
   */
  private static final List<String> DISABLED_INSPECTIONS = List.of(
    "SerializableCtor",
    "StatisticsCollectorNotRegistered"
  );

  /**
   * Inspections which implementation is in progress or are finished but not battle-tested yet and may require improvements/polishing.
   */
  private static final List<String> WIP_INSPECTIONS = List.of(
    "ExtensionClassShouldBeFinalAndNonPublic",
    "CanBeDumbAware",
    "CancellationCheckInLoops",
    "ThreadingConcurrency",
    "CallingMethodShouldBeRequiresBlockingContext",
    "PotentialDeadlockInServiceInitialization",
    "ObsoleteDispatchersEdt",
    "PathAnnotationInspection"
  );

  public void testNumberOfKnownDevKitInspections() {
    assertEquals(
      """
        Mismatch in total number of DevKit inspections.
        * If you've just added a DevKit inspection, then just increment DevkitInspectionsRegistrationCheckTest#EXPECTED_INSPECTIONS_NUMBER.
        * Otherwise, check classpath in test run configuration (intellij.devkit.plugin.main)""",
      EXPECTED_INSPECTIONS_NUMBER, getDevKitInspections().count()
    );
  }

  /**
   * Validates all DevKit inspections that are disabled by default match the expected known set.
   */
  public void testKnownDisabledByDefaultInspections() {
    var disabledInspectionShortNames = getDevKitInspections()
      .filter(ep -> !ep.enabledByDefault)
      .map(ep -> ep.getShortName())
      .toList();
    var allKnownDisabledInspections = ContainerUtil.concat(DISABLED_INSPECTIONS, WIP_INSPECTIONS);
    assertThat(disabledInspectionShortNames).containsExactlyInAnyOrderElementsOf(allKnownDisabledInspections);
  }

  private static Stream<LocalInspectionEP> getDevKitInspections() {
    return LocalInspectionEP.LOCAL_INSPECTION.getExtensionList().stream()
      .filter(ep -> "DevKit".equals(ep.getPluginDescriptor().getPluginId().getIdString()));
  }
}
