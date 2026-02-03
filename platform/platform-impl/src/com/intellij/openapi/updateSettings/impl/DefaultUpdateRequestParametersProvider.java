// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.JetBrainsPermanentInstallationID;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

@ApiStatus.Internal
public class DefaultUpdateRequestParametersProvider implements UpdateRequestParametersProvider {
  private static final NullableLazyValue<String> ourMachineId =
    lazyNullable(() -> MachineIdManager.INSTANCE.getAnonymizedMachineId("JetBrainsUpdates"));

  @Override
  public void amendUpdateRequest(@NotNull Map<String, String> parameters) {
    parameters.put("build", ApplicationInfo.getInstance().getBuild().asString());

    var os = OS.CURRENT;
    parameters.put("os", (os == OS.macOS ? "Mac OS X" : os.name()) + ' ' + os.version());

    if (ApplicationInfoEx.getInstanceEx().isEAP()) {
      parameters.put("eap", "");
    }

    parameters.put("uid", JetBrainsPermanentInstallationID.get());

    if (!PropertiesComponent.getInstance().getBoolean(UpdateCheckerFacade.MACHINE_ID_DISABLED_PROPERTY, false)) {
      var machineId = ourMachineId.getValue();
      if (machineId != null) {
        parameters.put("mid", machineId);
      }
    }

    if (ExternalUpdateManager.ACTUAL != null) {
      var name = ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX ? "Toolbox" : ExternalUpdateManager.ACTUAL.toolName;
      parameters.put("manager", name);
    }

    var facade = LicensingFacade.getInstance();
    if (facade != null) {
      @SuppressWarnings("removal") var subType = facade.subType;
      if (subType != null) {
        parameters.put("license", subType);
      }
      var metadata = facade.metadata;
      if (metadata != null) {
        parameters.put("metadata", metadata);
      }
      var userBucket = facade.userBucket;
      if (userBucket != null) {
        parameters.put("userBucket", userBucket);
      }
    }
  }
}
