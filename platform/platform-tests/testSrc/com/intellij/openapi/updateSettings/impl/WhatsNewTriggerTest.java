// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class WhatsNewTriggerTest {
  @BeforeEach void setUp(@TempDir Path tempDir) throws Exception {
    var tempUpdateData = tempDir.resolve("updates.xml");
    Files.writeString(
      tempUpdateData,
      "<products>\n" +
      "  <product name='IDEA'>\n" +
      "    <code>" + ApplicationInfo.getInstance().getBuild().getProductCode() + "</code>\n" +
      "    <channel id='-' licensing='release'>\n" +
      "      <build fullNumber='212.4746.92'/>\n" +
      "      <build fullNumber='211.7628.21'/>\n" +
      "    </channel>\n" +
      "  </product>\n" +
      "</products>");
    System.setProperty("idea.updates.url", tempUpdateData.toUri().toURL().toExternalForm());
  }

  @AfterEach void tearDown() {
    UpdateSettings.getInstance().setWhatsNewShownFor(0);
    System.clearProperty("idea.updates.url");
  }

  @Test void newReleaseInstallation() {
    var release = BuildNumber.fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test void newEapInstallation() {
    var beta = BuildNumber.fromString("212.4638.7");
    var release = BuildNumber.fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 2nd launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test void updateReleaseToRelease() {
    var previous = BuildNumber.fromString("211.7628.21");
    var release = BuildNumber.fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(previous, false));  // previous version last launch
    assertTrue(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test void updateReleaseToEapToRelease() {
    var previous = BuildNumber.fromString("211.7628.21");
    var beta = BuildNumber.fromString("212.4638.7");
    var release = BuildNumber.fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(previous, false));  // previous version last launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 1st launch
    assertTrue(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }
}
