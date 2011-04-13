/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run;

import com.android.ddmlib.*;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.GenericAttributeValue;
import org.jdom.Element;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.logcat.AndroidLogFilterModel;
import org.jetbrains.android.logcat.AndroidLogcatFiltersPreferences;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkImpl;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 2:20:54 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> {
  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";

  public boolean CHOOSE_DEVICE_MANUALLY = false;
  public String PREFERRED_AVD = "";
  public String COMMAND_LINE = "";
  public boolean WIPE_USER_DATA = false;
  public boolean DISABLE_BOOT_ANIMATION = false;
  public String NETWORK_SPEED = "full";
  public String NETWORK_LATENCY = "none";
  public boolean CLEAR_LOGCAT = false;

  public AndroidRunConfigurationBase(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, false), factory);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    configurationModule.checkForWarning();
    Module module = configurationModule.getModule();
    if (module == null) return;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.no.facet.error"));
    }
    if (facet.getConfiguration().LIBRARY_PROJECT) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.cannot.run.library.project.error"));
    }
    if (facet.getConfiguration().getAndroidPlatform() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("select.platform.error"));
    }
    if (facet.getManifest() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.manifest.not.found.error"));
    }
    if (PREFERRED_AVD.length() > 0) {
      AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        AndroidSdk sdk = facet.getConfiguration().getAndroidSdk();
        if (sdk instanceof AndroidSdkImpl) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.cannot.be.loaded.error"));
        }
      }
      if (avdManager != null) {
        AvdManager.AvdInfo avdInfo = avdManager.getAvd(PREFERRED_AVD, false);
        if (avdInfo == null) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.found.error", PREFERRED_AVD));
        }
        if (!facet.isCompatibleAvd(avdInfo)) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.compatible.error", PREFERRED_AVD));
        }
        if (avdInfo.getStatus() != AvdManager.AvdInfo.AvdStatus.OK) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.valid.error", PREFERRED_AVD));
        }
      }
    }
    checkConfiguration(facet);
  }

  protected abstract void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException;

  public Collection<Module> getValidModules() {
    final List<Module> result = new ArrayList<Module>();
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  private static boolean fillRuntimeAndTestDependencies(@NotNull Module module, @NotNull Map<AndroidFacet, String> module2PackageName) {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
          if (depFacet != null &&
              !module2PackageName.containsKey(depFacet) &&
              !depFacet.getConfiguration().LIBRARY_PROJECT &&
              moduleOrderEntry.getScope() != DependencyScope.COMPILE) {
            String packageName = getPackageName(depFacet);
            if (packageName == null) {
              return false;
            }
            module2PackageName.put(depFacet, packageName);
            if (!fillRuntimeAndTestDependencies(depModule, module2PackageName)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  /*private static boolean containsRealDevice(@NotNull IDevice[] devices) {
    for (IDevice device : devices) {
      if (!device.isEmulator()) {
        return true;
      }
    }
    return false;
  }*/

  public RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getConfigurationModule().getModule();
    if (module == null) {
      throw new ExecutionException("Module is not found");
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new ExecutionException(AndroidBundle.message("no.facet.error", module.getName()));
    }

    Project project = env.getProject();

    AndroidFacetConfiguration configuration = facet.getConfiguration();
    AndroidPlatform platform = configuration.getAndroidPlatform();
    if (platform == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      ModulesConfigurator.showFacetSettingsDialog(facet, null);
      return null;
    }

    if (platform.getSdk().getDebugBridge(getProject()) == null) return null;

    boolean debug = DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId());
    if (debug) {
      if (!activateDdmsIfNeccessary(facet)) {
        return null;
      }
    }

    String aPackage = getPackageName(facet);
    if (aPackage == null) return null;

    Map<AndroidFacet, String> depModule2PackageName = new HashMap<AndroidFacet, String>();
    if (!fillRuntimeAndTestDependencies(module, depModule2PackageName)) return null;

    IDevice[] targetDevices = new IDevice[0];
    if (CHOOSE_DEVICE_MANUALLY) {
      IDevice[] devices = chooseDevicesManually(facet);
      if (devices.length > 0) {
        targetDevices = devices;
        PropertiesComponent.getInstance(getProject()).setValue(ANDROID_TARGET_DEVICES_PROPERTY, toString(targetDevices));
      }
      if (targetDevices.length == 0) return null;
    }
    AndroidApplicationLauncher applicationLauncher = getApplicationLauncher(facet);
    if (applicationLauncher != null) {
      return new AndroidRunningState(env, facet, targetDevices, PREFERRED_AVD.length() > 0 ? PREFERRED_AVD : null,
                                     computeCommandLine(), aPackage, applicationLauncher, depModule2PackageName) {

        @NotNull
        @Override
        protected ConsoleView attachConsole() throws ExecutionException {
          return AndroidRunConfigurationBase.this.attachConsole(this, executor);
        }
      };
    }
    return null;
  }

  private static boolean activateDdmsIfNeccessary(@NotNull AndroidFacet facet) {
    final Project project = facet.getModule().getProject();
    final boolean ddmsEnabled = AndroidEnableDdmsAction.isDdmsEnabled();
    boolean shouldRestartDdms = !ddmsEnabled;

    if (ddmsEnabled && isDdmsCorrupted(facet)) {
      shouldRestartDdms = true;
      AndroidEnableDdmsAction.setDdmsEnabled(project, false);
    }

    if (shouldRestartDdms) {
      if (!ddmsEnabled) {
        int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                              AndroidBundle.message("android.ddms.disabled.dialog.title"),
                                              Messages.getQuestionIcon());
        if (result != 0) {
          return false;
        }
      }
      AndroidEnableDdmsAction.setDdmsEnabled(project, true);
    }
    return true;
  }

  // can be invoked only from dispatch thread!
  private static boolean isDdmsCorrupted(@NotNull AndroidFacet facet) {
    AndroidDebugBridge bridge = facet.getDebugBridge();
    if (bridge != null) {
      return isDdmsCorrupted(bridge);
    }
    return false;
  }

  static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      Client[] clients = devices[0].getClients();
      if (clients.length > 0) {
        ClientData clientData = clients[0].getClientData();
        return clientData == null || clientData.getVmIdentifier() == null;
      }
    }
    return false;
  }

  @Nullable
  private static String getPackageName(AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return null;
    GenericAttributeValue<String> packageAttrValue = manifest.getPackage();
    String aPackage = packageAttrValue.getValue();
    if (aPackage == null || aPackage.length() == 0) {
      Project project = facet.getModule().getProject();
      Messages.showErrorDialog(project, AndroidBundle.message("specify.main.package.error", facet.getModule().getName()),
                               CommonBundle.getErrorTitle());
      XmlAttributeValue attrValue = packageAttrValue.getXmlAttributeValue();
      if (attrValue != null) {
        PsiNavigateUtil.navigate(attrValue);
      }
      else {
        PsiNavigateUtil.navigate(manifest.getXmlElement());
      }
      return null;
    }
    return aPackage;
  }

  private String computeCommandLine() {
    StringBuilder result = new StringBuilder();
    result.append("-netspeed ").append(NETWORK_SPEED).append(' ');
    result.append("-netdelay ").append(NETWORK_LATENCY).append(' ');
    if (WIPE_USER_DATA) {
      result.append("-wipe-data ");
    }
    if (DISABLE_BOOT_ANIMATION) {
      result.append("-no-boot-anim ");
    }
    result.append(COMMAND_LINE);
    int last = result.length() - 1;
    if (result.charAt(last) == ' ') {
      result.deleteCharAt(last);
    }
    return result.toString();
  }

  @NotNull
  protected abstract ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException;

  @Nullable
  protected abstract AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet);

  protected abstract boolean supportMultipleDevices();

  private static String toString(IDevice[] devices) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = devices.length; i < n; i++) {
      builder.append(devices[i].getSerialNumber());
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static String[] fromString(String s) {
    return s.split(" ");
  }

  @NotNull
  private IDevice[] chooseDevicesManually(@NotNull AndroidFacet facet) {
    String value = PropertiesComponent.getInstance(getProject()).getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? fromString(value) : null;
    DeviceChooser chooser = new DeviceChooser(facet, supportMultipleDevices(), selectedSerials);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DeviceChooser.OK_EXIT_CODE || devices.length == 0) {
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    return devices;
  }

  @Override
  public void customizeLogConsole(LogConsole console) {
    final Project project = getProject();
    console.setFilterModel(new AndroidLogFilterModel(AndroidLogcatFiltersPreferences.getInstance(project).TAB_LOG_LEVEL) {
      @Override
      protected void setCustomFilter(String filter) {
        AndroidLogcatFiltersPreferences.getInstance(project).TAB_CUSTOM_FILTER = filter;
      }

      @Override
      protected void saveLogLevel(Log.LogLevel logLevel) {
        AndroidLogcatFiltersPreferences.getInstance(project).TAB_LOG_LEVEL = logLevel.name();
      }

      @Override
      public String getCustomFilter() {
        return AndroidLogcatFiltersPreferences.getInstance(project).TAB_CUSTOM_FILTER;
      }
    });
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
