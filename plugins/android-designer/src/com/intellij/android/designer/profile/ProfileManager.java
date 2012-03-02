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

import com.android.AndroidConstants;
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
 * @author Alexander Lobas
 */
public class ProfileManager {
  private static final LayoutDevice CUSTOM_DEVICE = new LayoutDevice("Edit...", LayoutDevice.Type.CUSTOM);

  private final Module myModule;

  private final AbstractComboBoxAction<LayoutDevice> myDeviceAction;
  private final AbstractComboBoxAction<LayoutDeviceConfiguration> myDeviceConfigurationAction;
  private final AbstractComboBoxAction<IAndroidTarget> myTargetAction;
  private final AbstractComboBoxAction<LocaleData> myLocaleAction;
  private final AbstractComboBoxAction<UiMode> myDockModeAction;
  private final AbstractComboBoxAction<NightMode> myNightModeAction;
  private final AbstractComboBoxAction<ThemeData> myThemeDataAction;

  private LayoutDeviceManager myLayoutDeviceManager = new LayoutDeviceManager();
  private List<LayoutDevice> myDevices;
  private List<LayoutDeviceConfiguration> myDeviceConfigurations;

  private Profile myProfile;

  public ProfileManager(Module module) {
    myModule = module;

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

          updatePlatform(getPlatform());

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

        return item != CUSTOM_DEVICE;
      }
    };

    myDeviceConfigurationAction = new MyComboBoxAction<LayoutDeviceConfiguration>() {
      @Override
      protected boolean selectionChanged(LayoutDeviceConfiguration item) {
        updateDeviceConfiguration(item);
        return true;
      }
    };

    myTargetAction = new MyComboBoxAction<IAndroidTarget>() {
      @Override
      protected boolean selectionChanged(IAndroidTarget item) {
        updateTarget(item);
        updateThemes();
        return true;
      }
    };

    myLocaleAction = new MyComboBoxAction<LocaleData>() {
      @Override
      protected boolean selectionChanged(LocaleData item) {
        updateLocale(item);
        return true;
      }
    };

    myDockModeAction = new MyComboBoxAction<UiMode>() {
      @Override
      protected boolean selectionChanged(UiMode item) {
        updateDockMode(item);
        return true;
      }
    };
    myDockModeAction.setItems(Arrays.asList(UiMode.values()), null);

    myNightModeAction = new MyComboBoxAction<NightMode>() {
      @Override
      protected boolean selectionChanged(NightMode item) {
        updateNightMode(item);
        return true;
      }
    };
    myNightModeAction.setItems(Arrays.asList(NightMode.values()), null);

    myThemeDataAction = new MyComboBoxAction<ThemeData>() {
      @Override
      protected boolean addSeparator(DefaultActionGroup actionGroup, ThemeData item) {
        return super.addSeparator(actionGroup, item);    //To change body of overridden methods use File | Settings | File Templates.
      }

      @Override
      protected boolean selectionChanged(ThemeData item) {
        return false;  // TODO: Auto-generated method stub
      }
    };
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void setProfile(Profile profile) {
    myProfile = profile;
    update(getPlatform());
    myDockModeAction.setSelection(UiMode.getEnum(myProfile.getDockMode()));
    myNightModeAction.setSelection(NightMode.getEnum(myProfile.getNightMode()));
  }

  public void update(@Nullable AndroidPlatform platform) {
    if (myProfile != null) {
      updateLocales();
      updatePlatform(platform);
      updateThemes();
    }
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
            String[] segments = resourceFile.getName().split(AndroidConstants.RES_QUALIFIER_SEP);
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

  private void updateDevice(@Nullable LayoutDevice device) {
    LayoutDeviceConfiguration configuration = myDeviceConfigurationAction.getSelection();
    updateDevice(device, configuration == null ? null : configuration.getName());
  }

  private void updateDevice(@Nullable LayoutDevice device, @Nullable String configurationName) {
    myProfile.setDevice(device == null ? null : device.getName());

    List<LayoutDeviceConfiguration> configurations = Collections.emptyList();
    LayoutDeviceConfiguration newConfiguration = null;

    if (device != null && configurationName != null) {
      configurations = device.getConfigurations();
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

  private void updateDockMode(UiMode mode) {
    myProfile.setDockMode(mode.getResourceValue());
  }

  private void updateNightMode(NightMode mode) {
    myProfile.setNightMode(mode.getResourceValue());
  }

  private void updateThemes() {
    // TODO: Auto-generated method stub
  }

  @Nullable
  public AndroidPlatform getPlatform() {
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
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
    protected void update(T item, Presentation presentation) {
      if (item == null) {
        presentation.setText("<html><font color='red'>[None]</font></html>");
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
      else if (item instanceof ThemeData) {
        // TODO
        ThemeData theme = (ThemeData)item;
        presentation.setText(theme.getName());
      }
    }
  }
}