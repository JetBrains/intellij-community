// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.BuildNumber.fromString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WhatsNewTriggerTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Before
  public void setUp() throws Exception {
    Path tempUpdateData = tempDir.newFile("updates.xml").toPath();
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

  @After
  public void tearDown() {
    UpdateSettings.getInstance().setWhatsNewShownFor(0);
    System.clearProperty("idea.updates.url");
  }

  @Test
  public void newReleaseInstallation() {
    BuildNumber release = fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test
  public void newEapInstallation() {
    BuildNumber beta = fromString("212.4638.7"), release = fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 2nd launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test
  public void updateReleaseToRelease() {
    BuildNumber previous = fromString("211.7628.21"), release = fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(previous, false));  // previous version last launch
    assertTrue(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test
  public void updateReleaseToEapToRelease() {
    BuildNumber previous = fromString("211.7628.21"), beta = fromString("212.4638.7"), release = fromString("212.4746.92");
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(previous, false));  // previous version last launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(beta, true));  // EAP 1st launch
    assertTrue(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    assertFalse(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 2nd launch
  }

  @Test
  public void whatsNewSettingsMigration() {
    BuildNumber previous = fromString("211.7628.21"), release = fromString("212.4746.92");
    String historicProperty = "ide.updates.whats.new.shown.for";
    PropertiesComponent properties = PropertiesComponent.getInstance();
    try {
      properties.setValue(historicProperty, previous.getBaselineVersion(), 0);
      assertTrue(UpdateCheckerService.shouldShowWhatsNew(release, false));  // release 1st launch
    }
    finally {
      properties.unsetValue(historicProperty);
    }
  }
}
