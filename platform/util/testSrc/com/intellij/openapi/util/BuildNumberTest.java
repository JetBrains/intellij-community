// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.google.common.primitives.Ints;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BuildNumberTest {
  @Test
  public void empty() {
    assertNull(BuildNumber.fromString(null));
    assertNull(BuildNumber.fromString(" "));
  }

  @Test
  public void historicBuild() {
    assertEquals(new BuildNumber("", 75, 7512), BuildNumber.fromString("7512"));
    assertEquals("75.7512", BuildNumber.fromString("7512").asString());
  }

  @Test
  public void branchBasedBuild() {
    assertParsed(BuildNumber.fromString("145"), 145, 0, "145.0");
    assertParsed(BuildNumber.fromString("145.1"), 145, 1, "145.1");
    assertParsed(BuildNumber.fromString("145.1.2"), 145, 1, "145.1.2");
    assertParsed(BuildNumber.fromString("IU-145.1.2"), 145, 1, "IU-145.1.2");
    assertParsed(BuildNumber.fromString("IU-145.*"), 145, BuildNumber.SNAPSHOT_VALUE, "IU-145.SNAPSHOT");
    assertParsed(BuildNumber.fromString("IU-145.SNAPSHOT"), 145, BuildNumber.SNAPSHOT_VALUE, "IU-145.SNAPSHOT");
    assertParsed(BuildNumber.fromString("IU-145.1.*"), 145, 1, "IU-145.1.SNAPSHOT");
    assertParsed(BuildNumber.fromString("IU-145.1.SNAPSHOT"), 145, 1, "IU-145.1.SNAPSHOT");

    assertParsed(BuildNumber.fromString("IU-146.1.2.3.4"), 146, 1, "IU-146.1.2.3.4");
    assertParsed(BuildNumber.fromString("IU-147.1000.2000.3000.4000"), 147, 1000, "IU-147.1000.2000.3000.4000");
  }

  private static void assertParsed(BuildNumber n, int expectedBaseline, int expectedBuildNumber, String asString) {
    assertEquals(expectedBaseline, n.getBaselineVersion());
    assertEquals(expectedBuildNumber, n.getComponents()[1]);
    assertEquals(asString, n.asString());
  }

  @Test
  public void comparingVersion() {
    assertTrue(BuildNumber.fromString("145.1").compareTo(BuildNumber.fromString("145.*")) < 0);
    assertTrue(BuildNumber.fromString("145.1.1").compareTo(BuildNumber.fromString("145.*")) < 0);
    assertTrue(BuildNumber.fromString("145.1.1.1.1").compareTo(BuildNumber.fromString("145.*")) < 0);
    assertTrue(BuildNumber.fromString("145.1").compareTo(BuildNumber.fromString("146.*")) < 0);
    assertTrue(BuildNumber.fromString("145.1").compareTo(BuildNumber.fromString("144.*")) > 0);
    assertTrue(BuildNumber.fromString("145.1.1.1").compareTo(BuildNumber.fromString("145.1.1.1.1")) < 0);
    assertTrue(BuildNumber.fromString("145.1.1.2").compareTo(BuildNumber.fromString("145.1.1.1.1")) > 0);
    assertTrue(BuildNumber.fromString("145.2.2.2.2").compareTo(BuildNumber.fromString("145.2.*")) < 0);
    assertTrue(BuildNumber.fromString("145.2.*").compareTo(BuildNumber.fromString("145.2.2.2.2")) > 0);
  }

  @Test
  public void isSnapshot() {
    assertTrue(BuildNumber.fromString("SNAPSHOT").isSnapshot());
    assertTrue(BuildNumber.fromString("__BUILD_NUMBER__").isSnapshot());
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").isSnapshot());
    assertTrue(BuildNumber.fromString("IU-145.1.2.3.4.SNAPSHOT").isSnapshot());
    assertFalse(BuildNumber.fromString("IU-145.1.2.3.4").isSnapshot());

    assertTrue(BuildNumber.fromString("IC-90.*").isSnapshot());
    assertFalse(BuildNumber.fromString("90.9999999").isSnapshot());
  }

  @Test
  public void devSnapshotVersion() {
    BuildNumber b = BuildNumber.fromString("__BUILD_NUMBER__");
    assertTrue(b.asString(), b.getBaselineVersion() >= 145 && b.getBaselineVersion() <= 3000);
    assertTrue(b.isSnapshot());

    assertEquals(BuildNumber.fromString("__BUILD_NUMBER__"), BuildNumber.fromString("SNAPSHOT"));
  }

  @Test
  @SuppressWarnings("SimplifiableJUnitAssertion")
  public void snapshotDomination() {
    assertTrue(BuildNumber.fromString("90.SNAPSHOT").compareTo(BuildNumber.fromString("90.12345")) > 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-90.12345")) > 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-100.12345")) < 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-100.SNAPSHOT")) < 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-90.SNAPSHOT")) == 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT").compareTo(BuildNumber.fromString("145.1")) > 0);
    assertTrue(BuildNumber.fromString("145.1").compareTo(BuildNumber.fromString("145.SNAPSHOT")) < 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT").compareTo(BuildNumber.fromString("145.*")) == 0);
    assertTrue(BuildNumber.fromString("145.*").compareTo(BuildNumber.fromString("145.SNAPSHOT")) == 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT").compareTo(BuildNumber.fromString("145.1.*")) > 0);
    assertTrue(BuildNumber.fromString("145.1.*").compareTo(BuildNumber.fromString("145.SNAPSHOT")) < 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT").compareTo(BuildNumber.fromString("145.SNAPSHOT")) == 0);

    assertTrue(BuildNumber.fromString("145.1.SNAPSHOT").compareTo(BuildNumber.fromString("145.1.*")) == 0);
    assertTrue(BuildNumber.fromString("145.1.*").compareTo(BuildNumber.fromString("145.1.SNAPSHOT")) == 0);

    assertTrue(BuildNumber.fromString("145.1.SNAPSHOT").compareTo(BuildNumber.fromString("145.*")) < 0);
    assertTrue(BuildNumber.fromString("145.*").compareTo(BuildNumber.fromString("145.1.SNAPSHOT")) > 0);

    assertTrue(BuildNumber.fromString("145.1.SNAPSHOT").compareTo(BuildNumber.fromString("145.1.1")) > 0);
    assertTrue(BuildNumber.fromString("145.1.1").compareTo(BuildNumber.fromString("145.1.SNAPSHOT")) < 0);

    assertTrue(BuildNumber.fromString("145.1.SNAPSHOT").compareTo(BuildNumber.fromString("145.1.SNAPSHOT")) == 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT.1").compareTo(BuildNumber.fromString("145.1.1")) > 0);
    assertTrue(BuildNumber.fromString("145.1.1").compareTo(BuildNumber.fromString("145.SNAPSHOT.1")) < 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT.1").compareTo(BuildNumber.fromString("145.1.SNAPSHOT")) > 0);
    assertTrue(BuildNumber.fromString("145.1.SNAPSHOT").compareTo(BuildNumber.fromString("145.SNAPSHOT.1")) < 0);

    assertTrue(BuildNumber.fromString("145.SNAPSHOT.1").compareTo(BuildNumber.fromString("145.SNAPSHOT.SNAPSHOT")) == 0);
    assertTrue(BuildNumber.fromString("145.SNAPSHOT.SNAPSHOT").compareTo(BuildNumber.fromString("145.SNAPSHOT.1")) == 0);
  }

  @Test
  public void currentVersion() {
    BuildNumber current = BuildNumber.currentVersion();
    assertTrue(current.isSnapshot());

    assertTrue(current.compareTo(BuildNumber.fromString("7512")) > 0);
    assertTrue(current.compareTo(BuildNumber.fromString("145")) > 0);
    assertTrue(current.compareTo(BuildNumber.fromString("145.12")) > 0);
  }

  @Test
  public void withProductCode() {
    BuildNumber IU_173_SNAPSHOT = BuildNumber.fromString("IU-173.SNAPSHOT");
    assertTrue(IU_173_SNAPSHOT.isSnapshot());
    assertEquals("IU", IU_173_SNAPSHOT.getProductCode());
    assertEquals(Ints.asList(IU_173_SNAPSHOT.getComponents()), Arrays.asList(173, BuildNumber.SNAPSHOT_VALUE));

    assertEquals(IU_173_SNAPSHOT, BuildNumber.fromStringWithProductCode("173.SNAPSHOT", "IU"));

    assertEquals(IU_173_SNAPSHOT, BuildNumber.fromStringWithProductCode("IU-173.SNAPSHOT", "IU"));

    assertEquals(IU_173_SNAPSHOT, BuildNumber.fromStringWithProductCode("IU-173.SNAPSHOT", "IC"));
  }
}