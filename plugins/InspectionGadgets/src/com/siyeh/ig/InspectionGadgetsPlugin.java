/*
* Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.siyeh.ig;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.util.Consumer;
import com.siyeh.ig.telemetry.InspectionGadgetsTelemetry;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"OverlyCoupledClass",
                    "OverlyCoupledMethod",
                    "OverlyLongMethod",
                    "ClassWithTooManyMethods"})
public class InspectionGadgetsPlugin implements ApplicationComponent {
  public static boolean getUpToDateTelemetryEnabled(@NotNull final Consumer<Boolean> consumer, @NotNull Disposable disposable) {
    final RegistryValue registryValue = Registry.get("inspectionGadgets.telemetry.enabled");
    registryValue.addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(RegistryValue value) {
        consumer.consume(Boolean.valueOf(value.asBoolean()));
      }
    }, disposable);
    return registryValue.asBoolean();
  }

  public static InspectionGadgetsPlugin getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionGadgetsPlugin.class);
  }

  @Override
  public void disposeComponent() {}

  @Override
  @NotNull
  public String getComponentName() {
    return "InspectionGadgets";
  }

  @NotNull
  public InspectionGadgetsTelemetry getTelemetry() {
    return InspectionGadgetsTelemetry.getInstance();
  }

  @Override
  public void initComponent() {
    boolean telemetryEnabled = getUpToDateTelemetryEnabled(new Consumer<Boolean>() {
      @Override
      public void consume(Boolean value) {
        boolean enabled = value.booleanValue();
        InspectionGadgetsTelemetry.setEnabled(enabled);
      }
    }, ApplicationManager.getApplication());
    InspectionGadgetsTelemetry.setEnabled(telemetryEnabled);
  }

  public boolean isTelemetryEnabled() {
    return InspectionGadgetsTelemetry.isEnabled();
  }
}
