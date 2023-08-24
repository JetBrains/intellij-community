// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevkitInspectionsRegistrationCheckTest extends BasePlatformTestCase {

  private static final List<String> DISABLED_INSPECTIONS =
    List.of("StatisticsCollectorNotRegistered",
            "UseCouple",
            "HighlightVisitorInternal",
            "PluginXmlI18n",
            "SerializableCtor");

  private static final List<String> WIP_INSPECTIONS =
    List.of("ExtensionClassShouldBeFinalAndNonPublic",
            "ActionPresentationInstantiatedInCtor",
            "CancellationCheckInLoops",
            "ApplicationServiceAsStaticFinalFieldOrProperty",
            "ThreadingConcurrency",
            "TokenSetInParserDefinition",
            "CallingMethodShouldBeRequiresBlockingContext",
            "IncorrectProcessCanceledExceptionHandling",
            "StaticInitializationInExtensions",
            "ListenerImplementationMustNotBeDisposable");

  /**
   * Validates all DevKit inspections that are disabled by default match the expected known set.
   */
  public void testKnownDisabledByDefaultInspections() {
    List<LocalInspectionEP> devkitInspections = ContainerUtil.filter(LocalInspectionEP.LOCAL_INSPECTION.getExtensionList(), ep -> {
      return "DevKit".equals(ep.getPluginDescriptor().getPluginId().getIdString());
    });
    assertEquals("Mismatch in total inspections, check classpath in test run configuration (intellij.devkit.plugin)", 65,
                 devkitInspections.size());

    List<LocalInspectionEP> disabledInspections = ContainerUtil.filter(devkitInspections, ep -> !ep.enabledByDefault);
    List<String> disabledInspectionShortNames = new ArrayList<>(ContainerUtil.map(disabledInspections, ep -> ep.getShortName()));
    Collections.sort(disabledInspectionShortNames);

    assertContainsElements("Mismatch in known disabled inspections", disabledInspectionShortNames, DISABLED_INSPECTIONS);

    List<String> allKnownDisabledInspections = new ArrayList<>(ContainerUtil.concat(DISABLED_INSPECTIONS, WIP_INSPECTIONS));
    Collections.sort(allKnownDisabledInspections);

    assertSameElements("Mismatch in known WIP inspections", disabledInspectionShortNames,
                       allKnownDisabledInspections);
  }
}
