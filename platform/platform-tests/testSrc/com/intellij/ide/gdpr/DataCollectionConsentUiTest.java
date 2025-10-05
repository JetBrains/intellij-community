// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.gdpr.ui.consents.ConsentForcedState;
import com.intellij.ide.gdpr.ui.consents.ConsentGroup;
import com.intellij.ide.gdpr.ui.consents.ConsentGroupUi;
import com.intellij.ide.gdpr.ui.consents.ConsentUi;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.LicensingFacade;

import java.util.List;

public class DataCollectionConsentUiTest extends BasePlatformTestCase {
  private static final String CONSENT_ID_USAGE_STATS = "rsch.send.usage.stat";
  private static final String CONSENT_ID_TRACE_DATA_COM_COLLECTION = "ai.trace.data.collection.and.use.com.policy";
  private static final String GROUP_CONSENT_ID_DATA_COLLECTION = "data.collection";

  private static void setupLicensingFacade(char customerAgreementOnDetailedDataSharing) {
    LicensingFacade licensingFacade = new LicensingFacade();
    licensingFacade.metadata = "A".repeat(27) + customerAgreementOnDetailedDataSharing;
    licensingFacade.platformProductCode = "IU";
    LicensingFacade.setInstance(licensingFacade);
  }

  private static void setupLicensingFacade(String metadata) {
    LicensingFacade licensingFacade = new LicensingFacade();
    licensingFacade.metadata = metadata;
    licensingFacade.platformProductCode = "IU";
    LicensingFacade.setInstance(licensingFacade);
  }

  public void testAgreementParsing() {
    setupLicensingFacade('Y');
    assertEquals(DataCollectionAgreement.YES, DataCollectionAgreement.getInstance());

    setupLicensingFacade('N');
    assertEquals(DataCollectionAgreement.NO, DataCollectionAgreement.getInstance());

    setupLicensingFacade('X');
    assertEquals(DataCollectionAgreement.NOT_SET, DataCollectionAgreement.getInstance());

    setupLicensingFacade('O');
    assertNull(DataCollectionAgreement.getInstance());

    setupLicensingFacade("the entire metadata is extremely long and no required symbol on 28th position");
    assertNull(DataCollectionAgreement.getInstance());

    setupLicensingFacade("");
    assertNull(DataCollectionAgreement.getInstance());
  }

  public void testGroupForcedStateDescription() {
    Consent fus = new Consent(CONSENT_ID_USAGE_STATS, Version.fromString("1.1"), "Send anonymous usage statistics", "text", false, false, "en");
    Consent trace = new Consent(CONSENT_ID_TRACE_DATA_COM_COLLECTION, Version.fromString("1.0"), "Send detailed code-related data", "text", false, false, "en");
    ConsentGroup group = new ConsentGroup(GROUP_CONSENT_ID_DATA_COLLECTION, List.of(fus, trace));

    setupLicensingFacade('X');
    ConsentGroupUi groupUi = ConsentSettingsUi.getConsentGroupUi(group);
    assertNotNull(groupUi);
    assertNull(groupUi.getForcedStateDescription());

    setupLicensingFacade('N');
    groupUi = ConsentSettingsUi.getConsentGroupUi(group);
    assertNotNull(groupUi);
    assertEquals(IdeBundle.message("gdpr.data.collection.consent.group.setting.disabled.warning.text"), groupUi.getForcedStateDescription());

    setupLicensingFacade('Y');
    groupUi = ConsentSettingsUi.getConsentGroupUi(group);
    assertNotNull(groupUi);
    assertEquals(IdeBundle.message("gdpr.data.collection.consent.group.setting.enabled.warning.text"), groupUi.getForcedStateDescription());
  }

  public void testUsageStatisticsConsentForcedStateDependsOnAgreement() {
    Consent fus = new Consent(CONSENT_ID_USAGE_STATS, Version.fromString("1.1"), "Send anonymous usage statistics", "text", false, false, "en");
    ConsentUi ui = ConsentSettingsUi.getConsentUi(fus);

    setupLicensingFacade('X');
    assertNull(ui.getForcedState());

    setupLicensingFacade('N');
    assertNull(ui.getForcedState());

    setupLicensingFacade('Y');
    var state = ui.getForcedState();
    assertNotNull(state);
    assertInstanceOf(state, ConsentForcedState.AlwaysEnabled.class);
    assertNull(state.getDescription());
  }

  public void testTraceDataCollectionConsentForcedStateDependsOnAgreement() {
    Consent trace = new Consent(CONSENT_ID_TRACE_DATA_COM_COLLECTION, Version.fromString("1.0"), "Send detailed code-related data", "text", false,
                        false, "en");
    ConsentUi ui = ConsentSettingsUi.getConsentUi(trace);

    setupLicensingFacade('X');
    assertNull(ui.getForcedState());

    setupLicensingFacade('N');
    var state = ui.getForcedState();
    assertNotNull(state);
    assertInstanceOf(state, ConsentForcedState.ExternallyDisabled.class);
    assertNull(state.getDescription());

    setupLicensingFacade('Y');
    state = ui.getForcedState();
    assertNotNull(state);
    assertInstanceOf(state, ConsentForcedState.AlwaysEnabled.class);
    assertNull(state.getDescription());
  }
}