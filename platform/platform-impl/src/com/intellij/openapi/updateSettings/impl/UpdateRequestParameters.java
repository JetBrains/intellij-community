// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

@ApiStatus.Internal
public final class UpdateRequestParameters {
  private static final NullableLazyValue<String> ourMachineId =
    lazyNullable(() -> MachineIdManager.INSTANCE.getAnonymizedMachineId("JetBrainsUpdates", ""));

  public static @NotNull Url amendUpdateRequest(@NotNull Url url) {
    var parameters = new LinkedHashMap<String, String>();

    parameters.put("build", ApplicationInfo.getInstance().getBuild().asString());

    parameters.put("os", SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION);

    if (ApplicationInfoEx.getInstanceEx().isEAP()) {
      parameters.put("eap", "");
    }

    parameters.put("uid", PermanentInstallationID.get());

    if (!PropertiesComponent.getInstance().getBoolean(UpdateChecker.MACHINE_ID_DISABLED_PROPERTY, false)) {
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
    }

    return url.addParameters(parameters);
  }
}
