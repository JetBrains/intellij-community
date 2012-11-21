/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.profile;


import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.State;
import com.intellij.designer.ModuleProvider;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.uipreview.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 * @author Alexander Lobas
 */
public class ProfileManager implements Disposable {
  private static final DeviceWrapper NO_DEVICES = new DeviceWrapper(null);

  private final ModuleProvider myModuleProvider;
  private final Runnable myRefreshAction;
  private final Runnable mySelectionRunnable;

  private Sdk mySdk;

  private final AbstractComboBoxAction<DeviceWrapper> myDeviceAction;
  private final AbstractComboBoxAction<State> myDeviceConfigurationAction;
  private final AbstractComboBoxAction<IAndroidTarget> myTargetAction;
  private final AbstractComboBoxAction<LocaleData> myLocaleAction;
  private final AbstractComboBoxAction<UiMode> myDockModeAction;
  private final AbstractComboBoxAction<NightMode> myNightModeAction;
  private final AbstractComboBoxAction<ThemeData> myThemeAction;

  private final DeviceManager myLayoutDeviceManager;
  private final UserDeviceManager myUserDeviceManager;
  private List<DeviceWrapper> myDevices;
  private ThemeManager myThemeManager;

  private Profile myProfile;

  public ProfileManager(ModuleProvider moduleProvider, Runnable refreshAction, Runnable selectionRunnable) {
    myModuleProvider = moduleProvider;
    myRefreshAction = refreshAction;
    mySelectionRunnable = selectionRunnable;

    myLayoutDeviceManager = ProfileList.getInstance(moduleProvider.getProject()).getLayoutDeviceManager();
    myUserDeviceManager = new UserDeviceManager() {
      @Override
      protected void userDevicesChanged() {
        updatePlatform(getPlatform(getSdk()));
      }
    };
    Disposer.register(this, myUserDeviceManager);

    myDeviceAction = new MyComboBoxAction<DeviceWrapper>() {
      @Override
      protected boolean selectionChanged(DeviceWrapper item) {
        if (item == NO_DEVICES) {
          return false;
        }
        updateDevice(item);
        setSelection(item);

        mySelectionRunnable.run();
        myRefreshAction.run();
        return true;
      }

      @Override
      protected int getMaxRows() {
        return 20;
      }
    };

    myDeviceConfigurationAction = new MyComboBoxAction<State>() {
      @Override
      protected boolean selectionChanged(State item) {
        updateDeviceConfiguration(item);
        mySelectionRunnable.run();
        setSelection(item);
        myRefreshAction.run();
        return true;
      }
    };

    myTargetAction = new MyComboBoxAction<IAndroidTarget>() {
      @Override
      protected boolean selectionChanged(IAndroidTarget item) {
        updateTarget(item);
        mySelectionRunnable.run();
        setSelection(item);
        updateThemes();
        return true;
      }
    };

    myLocaleAction = new MyComboBoxAction<LocaleData>() {
      @Override
      protected boolean selectionChanged(LocaleData item) {
        updateLocale(item);
        mySelectionRunnable.run();
        setSelection(item);
        myRefreshAction.run();
        return true;
      }
    };

    myDockModeAction = new MyComboBoxAction<UiMode>() {
      @Override
      protected boolean selectionChanged(UiMode item) {
        myProfile.setDockMode(item.getResourceValue());
        mySelectionRunnable.run();
        setSelection(item);
        myRefreshAction.run();
        return true;
      }
    };

    myNightModeAction = new MyComboBoxAction<NightMode>() {
      @Override
      protected boolean selectionChanged(NightMode item) {
        myProfile.setNightMode(item.getResourceValue());
        mySelectionRunnable.run();
        setSelection(item);
        myRefreshAction.run();
        return true;
      }
    };

    myThemeAction = new AbstractComboBoxAction<ThemeData>() {
      @Override
      protected void update(ThemeData theme, Presentation presentation, boolean popup) {
        presentation.setEnabled(theme != null && theme != ThemeManager.FRAMEWORK && theme != ThemeManager.PROJECT);

        if (theme != null) {
          if (!popup && !theme.isProjectTheme() && myThemeManager.getAddedThemes().contains(new ThemeData(theme.getName(), true))) {
            presentation.setText(theme.getName() + " (framework)");
          }
          else if (!popup || theme == ThemeManager.FRAMEWORK || theme == ThemeManager.PROJECT) {
            presentation.setText(theme.getName());
          }
          else {
            presentation.setText("      " + theme.getName());
          }
        }
        else {
          presentation.setText("[None]");
        }
      }

      @Override
      protected boolean selectionChanged(ThemeData item) {
        updateTheme(item);
        mySelectionRunnable.run();
        setSelection(item);
        myRefreshAction.run();
        return true;
      }

      @Override
      protected int getMaxRows() {
        return 20; // TODO: not worked
      }
    };
    myThemeAction.showDisabledActions(true);
  }

  public void setProfile(Profile profile) {
    myProfile = profile;

    if (myProfile == null) {
      myDeviceAction.clearSelection();
      myDeviceConfigurationAction.clearSelection();
      myTargetAction.clearSelection();
      myLocaleAction.clearSelection();
      myDockModeAction.clearSelection();
      myNightModeAction.clearSelection();
      myThemeAction.clearSelection();
    }
    else {
      update(mySdk);
    }
  }

  public void updateActions() {
    myDeviceAction.update();
    myDeviceConfigurationAction.update();
    myTargetAction.update();
    myLocaleAction.update();
    myDockModeAction.update();
    myNightModeAction.update();
    myThemeAction.update();
  }

  public void update(@Nullable Sdk sdk) {
    mySdk = sdk;
    if (myProfile != null) {
      updateLocales();
      updatePlatform(getPlatform(sdk));
      updateThemes();
      myDockModeAction.setItems(Arrays.asList(UiMode.values()), UiMode.getEnum(myProfile.getDockMode()));
      myNightModeAction.setItems(Arrays.asList(NightMode.values()), NightMode.getEnum(myProfile.getNightMode()));
    }
  }

  public Module getModule() {
    return myModuleProvider.getModule();
  }

  @Nullable
  public Sdk getSdk() {
    return mySdk;
  }

  @Nullable
  public State getSelectedDeviceConfiguration() {
    return myDeviceConfigurationAction.getSelection();
  }

  @Nullable
  public IAndroidTarget getSelectedTarget() {
    return myTargetAction.getSelection();
  }

  @Nullable
  public UiMode getSelectedDockMode() {
    return myDockModeAction.getSelection();
  }

  @Nullable
  public NightMode getSelectedNightMode() {
    return myNightModeAction.getSelection();
  }

  @Nullable
  public LocaleData getSelectedLocale() {
    return myLocaleAction.getSelection();
  }

  @Nullable
  public ThemeData getSelectedTheme() {
    return myThemeAction.getSelection();
  }

  public AbstractComboBoxAction<DeviceWrapper> getDeviceAction() {
    return myDeviceAction;
  }

  public AbstractComboBoxAction<State> getDeviceConfigurationAction() {
    return myDeviceConfigurationAction;
  }

  public AbstractComboBoxAction<IAndroidTarget> getTargetAction() {
    return myTargetAction;
  }

  public AbstractComboBoxAction<LocaleData> getLocaleAction() {
    return myLocaleAction;
  }

  public AbstractComboBoxAction<UiMode> getDockModeAction() {
    return myDockModeAction;
  }

  public AbstractComboBoxAction<NightMode> getNightModeAction() {
    return myNightModeAction;
  }

  public AbstractComboBoxAction<ThemeData> getThemeAction() {
    return myThemeAction;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void updateLocales() {
    List<LocaleData> locales = new ArrayList<LocaleData>();
    Map<String, Set<String>> language2Regions = new HashMap<String, Set<String>>();

    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    if (facet != null) {
      VirtualFile[] resourceDirs = facet.getLocalResourceManager().getAllResourceDirs();
      for (VirtualFile resourceDir : resourceDirs) {
        for (VirtualFile resourceFile : resourceDir.getChildren()) {
          if (resourceFile.isDirectory()) {
            String[] segments = resourceFile.getName().split(SdkConstants.RES_QUALIFIER_SEP);
            List<String> languageQualifiers = new ArrayList<String>();
            List<String> regionQualifiers = new ArrayList<String>();

            for (String segment : segments) {
              LanguageQualifier languageQualifier = LanguageQualifier.getQualifier(segment);
              if (languageQualifier != null) {
                languageQualifiers.add(languageQualifier.getValue());
              }
              RegionQualifier regionQualifier = RegionQualifier.getQualifier(segment);
              if (regionQualifier != null) {
                regionQualifiers.add(regionQualifier.getValue());
              }
            }

            for (String languageQualifier : languageQualifiers) {
              Set<String> regions = language2Regions.get(languageQualifier);
              if (regions == null) {
                regions = new HashSet<String>();
                language2Regions.put(languageQualifier, regions);
              }
              regions.addAll(regionQualifiers);
            }
          }
        }
      }

      for (Map.Entry<String, Set<String>> e : language2Regions.entrySet()) {
        String language = e.getKey();
        Set<String> regions = e.getValue();

        for (String region : regions) {
          String presentation = String.format("%1$s / %2$s", language, region);
          locales.add(new LocaleData(language, region, presentation));
        }
        String presentation = regions.isEmpty()
                              ? String.format("%1$s / Any", language)
                              : String.format("%1$s / Other", language);
        locales.add(new LocaleData(language, null, presentation));
      }

      Collections.sort(locales, new Comparator<LocaleData>() {
        @Override
        public int compare(LocaleData l1, LocaleData l2) {
          return l1.toString().compareTo(l2.toString());
        }
      });
    }
    locales.add(new LocaleData(null, null, language2Regions.isEmpty() ? "Any locale" : "Other locale"));

    LocaleData newLocale = null;
    if (myProfile.getLocaleLanguage() != null || myProfile.getLocaleRegion() != null) {
      newLocale = findLocale(locales, myProfile.getLocaleLanguage(), myProfile.getLocaleRegion());
    }
    if (newLocale == null) {
      newLocale = findLocale(locales, "en", null);
    }
    if (newLocale == null) {
      Locale defaultLocale = Locale.getDefault();
      newLocale = findLocale(locales, defaultLocale.getLanguage(), defaultLocale.getCountry());
    }
    if (newLocale == null && locales.size() > 0) {
      newLocale = locales.get(0);
    }

    myLocaleAction.setItems(locales, newLocale);
    updateLocale(newLocale);
  }

  @Nullable
  private static LocaleData findLocale(List<LocaleData> locales, String localeLanguage, @Nullable String localeRegion) {
    LocaleData localeWithoutRegion = null;
    for (LocaleData locale : locales) {
      if (StringUtil.equalsIgnoreCase(locale.getLanguage(), localeLanguage)) {
        if (StringUtil.equalsIgnoreCase(locale.getRegion(), localeRegion)) {
          return locale;
        }
        if (localeWithoutRegion == null) {
          localeWithoutRegion = locale;
        }
      }
    }
    return localeWithoutRegion;
  }

  private static void addWrappedDevices(Collection<DeviceWrapper> to, Collection<Device> from) {
    for (Device device : from) {
      to.add(new DeviceWrapper(device));
    }
  }

  private void updatePlatform(@Nullable AndroidPlatform platform) {
    AndroidSdkData sdkData = platform != null ? platform.getSdkData() : null;

    List<IAndroidTarget> targets = Collections.emptyList();

    if (sdkData != null) {
      myDevices = new ArrayList<DeviceWrapper>();
      addWrappedDevices(myDevices, myLayoutDeviceManager.getDefaultDevices());
      addWrappedDevices(myDevices, myLayoutDeviceManager.getVendorDevices(sdkData.getLocation()));
      addWrappedDevices(myDevices, myUserDeviceManager.parseUserDevices(new MessageBuildingSdkLog()));

      if (myDevices.isEmpty()) {
        myDevices.add(NO_DEVICES);
      }

      targets = new ArrayList<IAndroidTarget>();
      for (IAndroidTarget target : sdkData.getTargets()) {
        if (target.isPlatform()) {
          targets.add(target);
        }
      }
    }
    else {
      myDevices = Arrays.asList(NO_DEVICES);
    }

    DeviceWrapper newDevice = null;
    String deviceName = myProfile.getDevice();
    if (deviceName != null) {
      for (DeviceWrapper device : myDevices) {
        if (deviceName.equals(device.getName())) {
          newDevice = device;
          break;
        }
      }
    }
    if (newDevice == null && !myDevices.isEmpty()) {
      newDevice = myDevices.get(0);
    }
    myDeviceAction.setItems(myDevices, newDevice);
    updateDevice(newDevice, myProfile.getDeviceConfiguration());

    IAndroidTarget newTarget = null;
    String targetHashString = myProfile.getTargetHashString();
    if (targetHashString != null) {
      for (IAndroidTarget target : targets) {
        if (targetHashString.equals(target.hashString())) {
          newTarget = target;
          break;
        }
      }
    }
    if (newTarget == null) {
      IAndroidTarget targetFromModule = platform != null ? platform.getTarget() : null;
      if (targetFromModule != null) {
        String modulePlatformHash = null;
        if (targetFromModule.isPlatform()) {
          modulePlatformHash = targetFromModule.hashString();
        }
        else {
          IAndroidTarget parent = targetFromModule.getParent();
          if (parent != null) {
            modulePlatformHash = parent.hashString();
          }
        }

        if (modulePlatformHash != null) {
          targetFromModule = sdkData.findTargetByHashString(modulePlatformHash);
          if (targetFromModule != null && targets.contains(targetFromModule)) {
            newTarget = targetFromModule;
          }
        }
      }
    }
    if (newTarget == null && !targets.isEmpty()) {
      newTarget = targets.get(0);
    }
    myTargetAction.setItems(targets, newTarget);
    updateTarget(newTarget);
  }

  private void updateDevice(@Nullable DeviceWrapper device) {
    State configuration = myDeviceConfigurationAction.getSelection();
    updateDevice(device, configuration == null ? null : configuration.getName());
  }

  private void updateDevice(@Nullable DeviceWrapper wrapper, @Nullable String configurationName) {
    myProfile.setDevice(wrapper == null ? null : wrapper.getName());

    List<State> configurations;
    if (wrapper == null || wrapper.getDevice() == null) {
      configurations = Collections.emptyList();
    }
    else {
      configurations = wrapper.getDevice().getAllStates();
      if (configurations == null) {
        configurations = Collections.emptyList();
      }
    }

    State newConfiguration = null;

    if (configurationName != null) {
      for (State configuration : configurations) {
        if (configurationName.equals(configuration.getName())) {
          newConfiguration = configuration;
          break;
        }
      }
    }
    if (newConfiguration == null && !configurations.isEmpty()) {
      newConfiguration = configurations.get(0);
    }

    myDeviceConfigurationAction.setItems(configurations, newConfiguration);
    updateDeviceConfiguration(newConfiguration);
  }

  private void updateDeviceConfiguration(@Nullable State configuration) {
    myProfile.setDeviceConfiguration(configuration == null ? null : configuration.getName());
  }

  private void updateTarget(@Nullable IAndroidTarget target) {
    myProfile.setTargetHashString(target == null ? null : target.hashString());
  }

  private void updateLocale(@Nullable LocaleData locale) {
    myProfile.setLocaleLanguage(locale == null ? null : locale.getLanguage());
    myProfile.setLocaleRegion(locale == null ? null : locale.getRegion());
  }

  private void updateThemes() {
    myThemeManager = new ThemeManager(this);
    myThemeManager.loadThemes(new Runnable() {
      @Override
      public void run() {
        List<ThemeData> themes = myThemeManager.getThemes();

        ThemeData newTheme = null;
        String themeName = myProfile.getTheme();
        if (themeName != null) {
          for (ThemeData theme : themes) {
            if (themeName.equals(theme.getName())) {
              newTheme = theme;
              break;
            }
          }
        }
        if (newTheme == null && !themes.isEmpty()) {
          for (ThemeData theme : themes) {
            if (theme != ThemeManager.FRAMEWORK && theme != ThemeManager.PROJECT) {
              newTheme = theme;
              break;
            }
          }
        }

        myThemeAction.setItems(themes, newTheme);
        updateTheme(newTheme);
        myRefreshAction.run();
      }
    });
  }

  private void updateTheme(@Nullable ThemeData theme) {
    myProfile.setTheme(theme == null ? null : theme.getName());
  }

  @Nullable
  private AndroidPlatform getPlatform(@Nullable Sdk sdk) {
    if (sdk == null) {
      sdk = ProfileList.getInstance(myModuleProvider.getProject()).getModuleSdk(getModule());
    }
    if (isAndroidSdk(sdk)) {
      AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        return additionalData.getAndroidPlatform();
      }
    }
    return null;
  }

  public static boolean isAndroidSdk(@Nullable Sdk sdk) {
    return sdk != null && sdk.getSdkType() instanceof AndroidSdkType;
  }

  @Override
  public void dispose() {
  }

  private static abstract class MyComboBoxAction<T> extends AbstractComboBoxAction<T> {
    @Override
    protected void update(T item, Presentation presentation, boolean popup) {
      presentation.setEnabled(item != null);

      if (item == null) {
        presentation.setText("[None]");
      }
      else if (item instanceof DeviceWrapper) {
        DeviceWrapper device = (DeviceWrapper)item;
        presentation.setText(device.getName());
      }
      else if (item instanceof State) {
        State configuration = (State)item;
        presentation.setText(configuration.getName());
      }
      else if (item instanceof IAndroidTarget) {
        IAndroidTarget target = (IAndroidTarget)item;
        presentation.setText(target.getName());
      }
      else if (item instanceof LocaleData) {
        presentation.setText(item.toString());
      }
      else if (item instanceof UiMode) {
        UiMode mode = (UiMode)item;
        presentation.setText(mode.getLongDisplayValue());
      }
      else if (item instanceof NightMode) {
        NightMode mode = (NightMode)item;
        presentation.setText(mode.getLongDisplayValue());
      }
    }
  }
}