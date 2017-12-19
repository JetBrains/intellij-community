/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: 13-Dec-17
 */
public class ConsentsTest extends TestCase{
  private static final String JSON_CONSENTS_DATA = "[{\"consentId\":\"rsch.test.consent.option.for.intellij\",\"version\":\"1.0\",\"text\":\"This is a text of test consent option.\",\"printableName\":\"Test consent option\",\"accepted\":true,\"deleted\":false,\"acceptanceTime\":0},{\"consentId\":\"rsch.send.usage.stat\",\"version\":\"1.0\",\"text\":\"I consent to submit anonymous usage statistics to help JetBrains make better releases and refine the most important areas of the products. I agree that the following information will be sent\\n  * Information about which product features is used\\n  * General statistics (number of files, file types) of the solutions I am working on\\n  * General information about my hardware configuration (for example, amount of RAM, CPU speed and number of cores)\\n  * General information about my software configuration (for example, OS version)\",\"printableName\":\"Send anonymous usage statistics to JetBrains\",\"accepted\":false,\"deleted\":false,\"acceptanceTime\":0}]";
  private static final String JSON_MINOR_UPGRADE_CONSENTS_DATA = "[{\"consentId\":\"rsch.test.consent.option.for.intellij\",\"version\":\"1.5\",\"text\":\"This is an upgraded text of test consent option.\",\"printableName\":\"Test consent option\",\"accepted\":true,\"deleted\":false,\"acceptanceTime\":0}]";
  private static final String JSON_MAJOR_UPGRADE_CONSENTS_DATA = "[{\"consentId\":\"rsch.send.usage.stat\",\"version\":\"2.0\",\"text\":\"This is an major-upgraded text of usage stats option.\",\"printableName\":\"Test consent option\",\"accepted\":true,\"deleted\":false,\"acceptanceTime\":0}]";
  private static final String JSON_DELETED_CONSENTS_DATA = "[{\"consentId\":\"rsch.test.consent.option.for.intellij\",\"version\":\"1.0\",\"text\":\"This is a text of test consent option.\",\"printableName\":\"Test consent option\",\"accepted\":true,\"deleted\":true,\"acceptanceTime\":0},{\"consentId\":\"rsch.send.usage.stat\",\"version\":\"1.0\",\"text\":\"I consent to submit anonymous usage statistics to help JetBrains make better releases and refine the most important areas of the products. I agree that the following information will be sent\\n  * Information about which product features is used\\n  * General statistics (number of files, file types) of the solutions I am working on\\n  * General information about my hardware configuration (for example, amount of RAM, CPU speed and number of cores)\\n  * General information about my software configuration (for example, OS version)\",\"printableName\":\"Send anonymous usage statistics to JetBrains\",\"accepted\":false,\"deleted\":false,\"acceptanceTime\":0}]";

  private static final String CONSENT_ID_1 = "rsch.test.consent.option.for.intellij";
  private static final String CONSENT_ID_USAGE_STATS = "rsch.send.usage.stat";

  private static String createUpgradeJson(String id, boolean isAccepted) {
    final long tstamp = System.currentTimeMillis();
    return "[{\"consentId\":\""+id+"\",\"version\":\"1.5\",\"text\":\"This is an upgraded text of test consent option.\",\"printableName\":\"Test consent option\",\"accepted\":"+String.valueOf(isAccepted)+",\"deleted\":false,\"acceptanceTime\":"+tstamp+"}]";
  }

  public void testUpdateDefaultsAndConfirmedFromServer() throws InterruptedException {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions("", JSON_CONSENTS_DATA);
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    final Pair<Collection<Consent>, Boolean> beforeConfirm = options.getConsents();
    assertTrue("Consents should require confirmation", beforeConfirm.second);
    assertEquals(2, beforeConfirm.first.size());
    checkStorage(storage, JSON_CONSENTS_DATA, "", "");

    final Consent consentBeforeUpgrade = lookupConsent(CONSENT_ID_1, beforeConfirm.first);
    assertNotNull(consentBeforeUpgrade);
    assertEquals(Version.fromString("1.0"), consentBeforeUpgrade.getVersion());

    final boolean initialAcceptedState = consentBeforeUpgrade.isAccepted();

    // confirm
    options.setConsents(beforeConfirm.first);

    {
      final Pair<Collection<Consent>, Boolean> afterConfirm = options.getConsents();
      assertFalse("Consents should NOT require confirmation", afterConfirm.second);
      final Consent consentAfterCorfirm = lookupConsent(CONSENT_ID_1, afterConfirm.first);
      assertNotNull(consentAfterCorfirm);
      assertEquals(Version.fromString("1.0"), consentAfterCorfirm.getVersion());
      assertEquals(initialAcceptedState, consentAfterCorfirm.isAccepted());
    }

    // upgrade: both default state (version) and accepted state should be upgraded
    final boolean newAcceptedState = !initialAcceptedState;
    Thread.sleep(1L);// ensure timestamp changes
    options.applyServerUpdates(createUpgradeJson(CONSENT_ID_1, newAcceptedState));
    {
      final Pair<Collection<Consent>, Boolean> afterUpgrade = options.getConsents();
      assertFalse("Consents should NOT require confirmation", afterUpgrade.second);  // no confirmation on minor updates required
      final Consent consentAfterUpgrade = lookupConsent(CONSENT_ID_1, afterUpgrade.first);
      assertNotNull(consentAfterUpgrade);
      assertEquals(Version.fromString("1.5"), consentAfterUpgrade.getVersion());
      assertEquals(newAcceptedState, consentAfterUpgrade.isAccepted());
    }
  }

  public void testConsentMinorVersionUpgrade() {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions("", JSON_CONSENTS_DATA);
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    final Pair<Collection<Consent>, Boolean> beforeConfirm = options.getConsents();
    assertTrue("Consents should require confirmation", beforeConfirm.second);
    assertEquals(2, beforeConfirm.first.size());
    checkStorage(storage, JSON_CONSENTS_DATA, "", "");
    final Consent consentBeforeUpgrade = lookupConsent(CONSENT_ID_1, beforeConfirm.first);
    assertNotNull(consentBeforeUpgrade);
    assertEquals(Version.fromString("1.0"), consentBeforeUpgrade.getVersion());

    options.setConsents(beforeConfirm.first);
    final Pair<Collection<Consent>, Boolean> afterConfirm = options.getConsents();
    assertFalse("Consents should NOT require confirmation", afterConfirm.second);
    assertEquals(2, afterConfirm.first.size());
    assertEquals(JSON_CONSENTS_DATA, storage.myBundled);
    assertEquals("", storage.myDefaults);
    assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));

    options.applyServerUpdates(JSON_MINOR_UPGRADE_CONSENTS_DATA);
    
    final Pair<Collection<Consent>, Boolean> afterUpdate = options.getConsents();
    assertFalse("Consents should NOT require confirmation", afterUpdate.second);
    assertEquals(2, afterUpdate.first.size());
    assertEquals(JSON_CONSENTS_DATA, storage.myBundled);
    assertFalse("The storage should contain non-empty default consents", StringUtil.isEmpty(storage.myDefaults));
    assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));
    final Consent consentAfterUpgrade = lookupConsent(CONSENT_ID_1, afterUpdate.first);
    assertNotNull(consentAfterUpgrade);
    assertEquals(Version.fromString("1.5"), consentAfterUpgrade.getVersion());
  }

  public void testConsentMajorVersionUpgrade() {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions("", JSON_CONSENTS_DATA);
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    {
      final Pair<Collection<Consent>, Boolean> beforeConfirm = options.getConsents();
      assertTrue("Consents should require confirmation", beforeConfirm.second);
      assertEquals(2, beforeConfirm.first.size());
      checkStorage(storage, JSON_CONSENTS_DATA, "", "");
      final Consent consentBeforeUpgrade = lookupConsent(CONSENT_ID_USAGE_STATS, beforeConfirm.first);
      assertNotNull(consentBeforeUpgrade);
      assertEquals(Version.fromString("1.0"), consentBeforeUpgrade.getVersion());
      assertFalse(consentBeforeUpgrade.isAccepted());
      assertEquals(ConsentOptions.Permission.UNDEFINED, options.isSendingUsageStatsAllowed());

      // confirm
      options.setConsents(beforeConfirm.first);
    }

    // after-confirmation state
    {
      final Pair<Collection<Consent>, Boolean> afterConfirm = options.getConsents();
      assertFalse("Consents should NOT require confirmation", afterConfirm.second);
      assertEquals(2, afterConfirm.first.size());
      assertEquals(JSON_CONSENTS_DATA, storage.myBundled);
      assertEquals("", storage.myDefaults);
      assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));
      assertEquals(ConsentOptions.Permission.NO, options.isSendingUsageStatsAllowed());
    }

    // updates from server
    {
      options.applyServerUpdates(JSON_MAJOR_UPGRADE_CONSENTS_DATA);
      final Pair<Collection<Consent>, Boolean> afterUpdate = options.getConsents();
      assertTrue("Consents should require confirmation", afterUpdate.second);
      assertEquals(2, afterUpdate.first.size());
      assertEquals(JSON_CONSENTS_DATA, storage.myBundled);
      assertFalse("The storage should contain non-empty default consents", StringUtil.isEmpty(storage.myDefaults));
      assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));
      final Consent consentAfterUpgrade = lookupConsent(CONSENT_ID_USAGE_STATS, afterUpdate.first);
      assertNotNull(consentAfterUpgrade);
      assertEquals(Version.fromString("2.0"), consentAfterUpgrade.getVersion());
      assertFalse(consentAfterUpgrade.isAccepted()); // although default value is now 'true', the last accepted value should be returned
      assertEquals(ConsentOptions.Permission.NO, options.isSendingUsageStatsAllowed()); // older confirmed value should be returned until consent is re-confirmed

      // second confirmation
      final List<Consent> toAccept = new ArrayList<>();
      for (Consent consent : afterUpdate.first) {
        if (CONSENT_ID_USAGE_STATS.equals(consent.getId())) {
          toAccept.add(consent.derive(true));
        }
        else {
          toAccept.add(consent);
        }
      }
      options.setConsents(toAccept);
    }

    {
      final Pair<Collection<Consent>, Boolean> afterSecondConfirm = options.getConsents();
      assertFalse("Consents should NOT require confirmation", afterSecondConfirm.second);
      assertEquals(2, afterSecondConfirm.first.size());
      assertEquals(JSON_CONSENTS_DATA, storage.myBundled);
      assertFalse("The storage should contain non-empty default consents", StringUtil.isEmpty(storage.myDefaults));
      assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));
      final Consent consentAfterSecondConfirm = lookupConsent(CONSENT_ID_USAGE_STATS, afterSecondConfirm.first);
      assertNotNull(consentAfterSecondConfirm);
      assertEquals(Version.fromString("2.0"), consentAfterSecondConfirm.getVersion());
      assertTrue(consentAfterSecondConfirm.isAccepted());
      assertEquals(ConsentOptions.Permission.YES, options.isSendingUsageStatsAllowed());
    }
  }

  public void testUsageStatsPermission() {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions(JSON_CONSENTS_DATA, "");
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    final Pair<Collection<Consent>, Boolean> beforeConfirm = options.getConsents();
    assertTrue("Consents should require confirmation", beforeConfirm.second);
    assertEquals(2, beforeConfirm.first.size());
    assertEquals(JSON_CONSENTS_DATA, storage.myDefaults);
    assertEquals("", storage.myConfirmed);

    assertNotNull(lookupConsent(CONSENT_ID_USAGE_STATS, beforeConfirm.first));
    assertEquals(ConsentOptions.Permission.UNDEFINED, options.isSendingUsageStatsAllowed());

    options.setConsents(beforeConfirm.first);
    final Pair<Collection<Consent>, Boolean> afterConfirm = options.getConsents();
    assertFalse("Consents should NOT require confirmation", afterConfirm.second);
    assertEquals(2, afterConfirm.first.size());
    assertEquals("", storage.myBundled);
    assertEquals(JSON_CONSENTS_DATA, storage.myDefaults);
    assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));
    assertEquals(ConsentOptions.Permission.NO, options.isSendingUsageStatsAllowed());
  }

  public void testDeletedConsentsNotVisible() {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions(JSON_DELETED_CONSENTS_DATA, JSON_CONSENTS_DATA);
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    final Pair<Collection<Consent>, Boolean> consents = options.getConsents();
    assertTrue("Consents should require confirmation", consents.second);
    assertEquals(1, consents.first.size());
    assertEquals(JSON_DELETED_CONSENTS_DATA, storage.myDefaults);
    assertEquals("", storage.myConfirmed);

    assertNull(lookupConsent(CONSENT_ID_1, consents.first));
    assertNotNull(lookupConsent(CONSENT_ID_USAGE_STATS, consents.first));
  }

  public void testLoadReadAndConfirm() {
    final Pair<ConsentOptions, MemoryIOBackend> data = createConsentOptions(JSON_CONSENTS_DATA, "");
    final ConsentOptions options = data.first;
    final MemoryIOBackend storage = data.second;

    final Pair<Collection<Consent>, Boolean> beforeConfirm = options.getConsents();
    assertTrue("Consents should require confirmation", beforeConfirm.second);
    assertEquals(2, beforeConfirm.first.size());
    assertEquals("", storage.myBundled);
    assertEquals(JSON_CONSENTS_DATA, storage.myDefaults);
    assertEquals("", storage.myConfirmed);

    final List<Consent> changedByUser = new ArrayList<>();
    for (Consent consent : beforeConfirm.first) {
      changedByUser.add(consent.derive(!consent.isAccepted()));
    }
    options.setConsents(changedByUser);
    
    final Pair<Collection<Consent>, Boolean> afterConfirm = options.getConsents();
    assertFalse("Consents should NOT require confirmation", afterConfirm.second);
    assertEquals(2, afterConfirm.first.size());
    assertEquals("", storage.myBundled);
    assertEquals(JSON_CONSENTS_DATA, storage.myDefaults);
    assertFalse("The storage should contain non-empty confirmed consents", StringUtil.isEmpty(storage.myConfirmed));

    for (Consent userConsent : changedByUser) {
      final Consent loaded = lookupConsent(userConsent, afterConfirm.first);
      assertEquals(userConsent, loaded);
      assertEquals(userConsent.isAccepted(), loaded.isAccepted());
    }
  }

  private static Consent lookupConsent(@NotNull String consentId, @NotNull Collection<Consent> container) {
    for (Consent c : container) {
      if (consentId.equals(c.getId())) {
        return c;
      }
    }
    return null;
  }

  private static Consent lookupConsent(@NotNull Consent consent, @NotNull Collection<Consent> container) {
    for (Consent c : container) {
      if (c.equals(consent)) {
        return c;
      }
    }
    return null;
  }

  private static void checkStorage(MemoryIOBackend storage, final String expectedBundled, final String expectedDefaults, final String expectedConfirmed) {
    assertEquals(expectedBundled, storage.myBundled);
    assertEquals(expectedDefaults, storage.myDefaults);
    assertEquals(expectedConfirmed, storage.myConfirmed);
  }

  private static Pair<ConsentOptions, MemoryIOBackend> createConsentOptions(String initialDefauls, String initialBundled) {
    final MemoryIOBackend backend = new MemoryIOBackend(initialDefauls, initialBundled);
    return Pair.create(new ConsentOptions(backend), backend);
  }

  private static class MemoryIOBackend implements ConsentOptions.IOBackend {
    final String myBundled;
    String myDefaults = "";
    String myConfirmed = "";

    public MemoryIOBackend(@NotNull String defs, @NotNull String bundled) {
      myDefaults = defs;
      myBundled = bundled;
    }

    public void writeDefaultConsents(@NotNull String data) {
      myDefaults = data;
    }

    @NotNull
    public String readDefaultConsents() {
      return myDefaults;
    }

    @NotNull
    public String readBundledConsents() {
      return myBundled;
    }

    public void writeConfirmedConsents(@NotNull String data) {
      myConfirmed = data;
    }

    @NotNull
    public String readConfirmedConsents() {
      return myConfirmed;
    }
  }
}
