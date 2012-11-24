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
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.uipreview.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 * @author Alexander Lobas
 */
public class ProfileManager {
  private static final LayoutDevice CUSTOM_DEVICE = new LayoutDevice("Edit Devices", LayoutDevice.Type.CUSTOM);

  private final Module myModule;
  private final Runnable myRefreshAction;
  private final Runnable mySelectionRunnable;

  private Sdk mySdk;

  private final AbstractComboBoxAction<LayoutDevice> myDeviceAction;
  private final AbstractComboBoxAction<LayoutDeviceConfiguration> myDeviceConfigurationAction;
  private final AbstractComboBoxAction<IAndroidTarget> myTargetAction;
  private final AbstractComboBoxAction<LocaleData> myLocaleAction;
  private final AbstractComboBoxAction<UiMode> myDockModeAction;
  private final AbstractComboBoxAction<NightMode> myNightModeAction;
  private final AbstractComboBoxAction<ThemeData> myThemeAction;

  private final LayoutDeviceManager myLayoutDeviceManager;
  private List<LayoutDevice> myDevices;
  private ThemeManager myThemeManager;

  private Profile myProfile;

  public ProfileManager(Module module, Runnable refreshAction, Runnable selectionRunnable) {
    myModule = module;
    myRefreshAction = refreshAction;
    mySelectionRunnable = selectionRunnable;

    myLayoutDeviceManager = ProfileList.getInstance(module.getProject()).getLayoutDeviceManager();

    myDeviceAction = new MyComboBoxAction<LayoutDevice>() {
      @Override
      protected boolean addSeparator(DefaultActionGroup actionGroup, LayoutDevice item) {
        if (item == CUSTOM_DEVICE && myDevices.size() > 1) {
          actionGroup.addSeparator();
        }
        return false;
      }

      @Override
      protected boolean selectionChanged(LayoutDevice item) {
        if (item == CUSTOM_DEVICE) {
          LayoutDeviceConfiguration configuration = myDeviceConfigurationAction.getSelection();
          configuration = configuration != null && configuration.getDevice().getType() == LayoutDevice.Type.CUSTOM ? configuration : null;
          LayoutDeviceConfigurationsDialog dialog =
            new LayoutDeviceConfigurationsDialog(myModule.getProject(), configuration, myLayoutDeviceManager);
          dialog.show();

          if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            myLayoutDeviceManager.saveUserDevices();
          }

          updatePlatform(getPlatform(null));

          String deviceName = dialog.getSelectedDeviceName();
          if (deviceName != null) {
            LayoutDevice newDevice = null;
            for (LayoutDevice device : myDevices) {
              if (device.getName().equals(deviceName)) {
                newDevice = device;
                break;
              }
            }

            if (newDevice != null) {
              String configurationName = dialog.getSelectedDeviceConfigName();
              if (configurationName == null) {
                updateDevice(newDevice);
              }
              else {
                updateDevice(newDevice, configurationName);
              }
            }
          }
        }
        else {
          updateDevice(item);
        }

        mySelectionRunnable.run();
        myRefreshAction.run();
        return item != CUSTOM_DEVICE;
      }

      @Override
      protected int getMaxRows() {
        return 20;
      }
    };

    myDeviceConfigurationAction = new MyComboBoxAction<LayoutDeviceConfiguration>() {
      @Override
      protected boolean selectionChanged(LayoutDeviceConfiguration item) {
        updateDeviceConfiguration(item);
        mySelectionRunnable.run();
        myRefreshAction.run();
        return true;
      }
    };

    myTargetAction = new MyComboBoxAction<IAndroidTarget>() {
      @Override
      protected boolean selectionChanged(IAndroidTarget item) {
        updateTarget(item);
        updateThemes();
        mySelectionRunnable.run();
        return true;
      }
    };

    myLocaleAction = new MyComboBoxAction<LocaleData>() {
      @Override
      protected boolean selectionChanged(LocaleData item) {
        updateLocale(item);
        mySelectionRunnable.run();
        myRefreshAction.run();
        return true;
      }
    };

    myDockModeAction = new MyComboBoxAction<UiMode>() {
      @Override
      protected boolean selectionChanged(UiMode item) {
        myProfile.setDockMode(item.getResourceValue());
        mySelectionRunnable.run();
        myRefreshAction.run();
        return true;
      }
    };

    myNightModeAction = new MyComboBoxAction<NightMode>() {
      @Override
      protected boolean selectionChanged(NightMode item) {
        myProfile.setNightMode(item.getResourceValue());
        mySelectionRunnable.run();
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

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

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
      update(null);
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
    return myModule;
  }

  @Nullable
  public Sdk getSdk() {
    return mySdk;
  }

  @Nullable
  public LayoutDeviceConfiguration getSelectedDeviceConfiguration() {
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

  public AbstractComboBoxAction<LayoutDevice> getDeviceAction() {
    return myDeviceAction;
  }

  public AbstractComboBoxAction<LayoutDeviceConfiguration> getDeviceConfigurationAction() {
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

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
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
    if (myProfile.getLocaleLanguage() != null && myProfile.getLocaleRegion() != null) {
      LocaleData oldLocale = new LocaleData(myProfile.getLocaleLanguage(), myProfile.getLocaleRegion(), "");
      for (LocaleData locale : locales) {
        if (locale.equals(oldLocale)) {
          newLocale = locale;
          break;
        }
      }
    }
    if (newLocale == null) {
      Locale defaultLocale = Locale.getDefault();
      LocaleData defaultData = new LocaleData(defaultLocale.getLanguage(), defaultLocale.getCountry(), "");
      for (LocaleData locale : locales) {
        if (locale.equals(defaultData)) {
          newLocale = locale;
          break;
        }
      }
    }
    if (newLocale == null && locales.size() > 0) {
      newLocale = locales.get(0);
    }

    myLocaleAction.setItems(locales, newLocale);
    updateLocale(newLocale);
  }

  private void updatePlatform(@Nullable AndroidPlatform platform) {
    AndroidSdkData sdkData = platform != null ? platform.getSdkData() : null;

    List<IAndroidTarget> targets = Collections.emptyList();

    if (sdkData != null) {
      myLayoutDeviceManager.loadDevices(sdkData);
      myDevices = new ArrayList<LayoutDevice>(myLayoutDeviceManager.getCombinedList());
      myDevices.add(CUSTOM_DEVICE);

      targets = new ArrayList<IAndroidTarget>();
      for (IAndroidTarget target : sdkData.getTargets()) {
        if (target.isPlatform()) {
          targets.add(target);
        }
      }
    }
    else {
      myDevices = Collections.emptyList();
    }

    LayoutDevice newDevice = null;
    String deviceName = myProfile.getDevice();
    if (deviceName != null) {
      for (LayoutDevice device : myDevices) {
        if (deviceName.equals(device.getName())) {
          newDevice = device;
          break;
        }
      }
    }
    if (newDevice == null && !myDevices.isEmpty()) {
      for (LayoutDevice device : myDevices) {
        if (device != CUSTOM_DEVICE) {
          newDevice = device;
          break;
        }
      }
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

  private void updateDevice(@Nullable LayoutDevice device) {
    LayoutDeviceConfiguration configuration = myDeviceConfigurationAction.getSelection();
    updateDevice(device, configuration == null ? null : configuration.getName());
  }

  private void updateDevice(@Nullable LayoutDevice device, @Nullable String configurationName) {
    myProfile.setDevice(device == null ? null : device.getName());

    List<LayoutDeviceConfiguration> configurations =
      device == null ? Collections.<LayoutDeviceConfiguration>emptyList() : device.getConfigurations();
    LayoutDeviceConfiguration newConfiguration = null;

    if (configurationName != null) {
      for (LayoutDeviceConfiguration configuration : configurations) {
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

  private void updateDeviceConfiguration(@Nullable LayoutDeviceConfiguration configuration) {
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
      sdk = ProfileList.getInstance(myModule.getProject()).getModuleSdk(myModule);
    }
    if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
      AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        return additionalData.getAndroidPlatform();
      }
    }
    return null;
  }

  private static abstract class MyComboBoxAction<T> extends AbstractComboBoxAction<T> {
    @Override
    protected void update(T item, Presentation presentation, boolean popup) {
      presentation.setEnabled(item != null);

      if (item == null) {
        presentation.setText("[None]");
      }
      else if (item instanceof LayoutDevice) {
        LayoutDevice device = (LayoutDevice)item;
        presentation.setText(device.getName());
      }
      else if (item instanceof LayoutDeviceConfiguration) {
        LayoutDeviceConfiguration configuration = (LayoutDeviceConfiguration)item;
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