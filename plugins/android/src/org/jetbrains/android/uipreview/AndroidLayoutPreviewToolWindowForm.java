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
package org.jetbrains.android.uipreview;

import com.android.AndroidConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.resources.NightMode;
import com.android.resources.ResourceType;
import com.android.resources.ScreenSize;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import icons.AndroidIcons;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidLayoutPreviewToolWindowForm implements Disposable {
  private static final String CUSTOM_DEVICE_STRING = "Edit...";

  private JPanel myContentPanel;
  private AndroidLayoutPreviewPanel myPreviewPanel;

  private JBScrollPane myScrollPane;

  private ComboBox myDevicesCombo;
  private List<LayoutDevice> myDevices = Collections.emptyList();

  private ComboBox myDeviceConfigurationsCombo;
  private List<LayoutDeviceConfiguration> myDeviceConfigurations = Collections.emptyList();

  private ComboBox myTargetCombo;
  private List<IAndroidTarget> myTargets = Collections.emptyList();

  private ComboBox myLocaleCombo;
  private List<LocaleData> myLocales = Collections.emptyList();

  private boolean myResetFlag;
  private boolean myThemesResetFlag;

  private ComboBox myThemeCombo;
  private ComboBox myDockModeCombo;
  private ComboBox myNightCombo;
  private JPanel myComboPanel;

  private PsiFile myFile;

  private AndroidPlatform myPrevPlatform = null;
  private LayoutDeviceManager myLayoutDeviceManager = new LayoutDeviceManager();
  private final AndroidLayoutPreviewToolWindowManager myToolWindowManager;
  private final ActionToolbar myActionToolBar;

  private final AndroidLayoutPreviewToolWindowSettings mySettings;

  public AndroidLayoutPreviewToolWindowForm(final Project project, AndroidLayoutPreviewToolWindowManager toolWindowManager) {
    Disposer.register(this, myPreviewPanel);

    myToolWindowManager = toolWindowManager;
    mySettings = AndroidLayoutPreviewToolWindowSettings.getInstance(project);

    final GridBagConstraints gb = new GridBagConstraints();

    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.anchor = GridBagConstraints.CENTER;
    gb.insets = new Insets(0, 2, 2, 2);
    gb.gridy = 0;
    gb.weightx = 1;
    gb.gridx = 0;
    gb.gridwidth = 1;

    myDevicesCombo = new ComboBox();
    myComboPanel.add(myDevicesCombo, gb);

    gb.gridx++;
    myDeviceConfigurationsCombo = new ComboBox();
    myComboPanel.add(myDeviceConfigurationsCombo, gb);

    gb.gridx++;
    myTargetCombo = new ComboBox();
    myComboPanel.add(myTargetCombo, gb);

    gb.gridx = 0;
    gb.gridy++;

    myLocaleCombo = new ComboBox();
    myComboPanel.add(myLocaleCombo, gb);

    gb.gridx++;
    myDockModeCombo = new ComboBox();
    myComboPanel.add(myDockModeCombo, gb);

    gb.gridx++;
    myNightCombo = new ComboBox();
    myComboPanel.add(myNightCombo, gb);

    myDevicesCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LayoutDevice) {
          final LayoutDevice device = (LayoutDevice)value;
          setText(device.getName());
        }
        else if (index == -1 || !CUSTOM_DEVICE_STRING.equals(value)) {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    myDeviceConfigurationsCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LayoutDeviceConfiguration) {
          final LayoutDeviceConfiguration deviceConfiguration = (LayoutDeviceConfiguration)value;
          setText(deviceConfiguration.getName());
        }
        else {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    myDevicesCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Object selectedItem = myDevicesCombo.getSelectedItem();
        if (selectedItem instanceof LayoutDevice) {
          updateDeviceConfigurations((LayoutDevice)selectedItem);
          saveState();
          myToolWindowManager.render();
        }
      }
    });

    myDevicesCombo.addItemListener(new ItemListener() {
      private LayoutDevice myPrevDevice = null;

      @Override
      public void itemStateChanged(ItemEvent e) {
        final Object item = e.getItem();
        if (item instanceof LayoutDevice) {
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            myPrevDevice = (LayoutDevice)item;
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {
          // "Custom..." element selected
          if (myPrevDevice != null) {
            myDevicesCombo.setSelectedItem(myPrevDevice);
          }
          else if (myDevices.size() > 0) {
            myDevicesCombo.setSelectedItem(myDevices.get(0));
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final LayoutDeviceConfiguration selectedConfig = getSelectedDeviceConfiguration();
              final LayoutDeviceConfiguration configToSelectInDialog =
                selectedConfig != null && selectedConfig.getDevice().getType() == LayoutDevice.Type.CUSTOM ? selectedConfig : null;
              final LayoutDeviceConfigurationsDialog dialog =
                new LayoutDeviceConfigurationsDialog(project, configToSelectInDialog, myLayoutDeviceManager);
              dialog.show();

              if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
                myLayoutDeviceManager.saveUserDevices();
              }

              final AndroidPlatform platform = myFile != null ? getPlatform(myFile) : null;
              updateDevicesAndTargets(platform);

              final String selectedDeviceName = dialog.getSelectedDeviceName();
              if (selectedDeviceName != null) {
                final LayoutDevice selectedDevice = findDeviceByName(selectedDeviceName);
                if (selectedDevice != null) {
                  myDevicesCombo.setSelectedItem(selectedDevice);
                }
              }

              final String selectedDeviceConfigName = dialog.getSelectedDeviceConfigName();
              if (selectedDeviceConfigName != null) {
                final LayoutDeviceConfiguration selectedDeviceConfig = findDeviceConfigByName(selectedDeviceConfigName);
                if (selectedDeviceConfig != null) {
                  myDeviceConfigurationsCombo.setSelectedItem(selectedDeviceConfig);
                }
              }
            }
          });
        }
      }
    });

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyZoomToFitAction());
    actionGroup.add(new MyZoomActualAction());
    actionGroup.add(new MyZoomInAction());
    actionGroup.add(new MyZoomOutAction());
    actionGroup.add(new MyRefreshAction());
    myActionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myActionToolBar.setReservePlaceAutoPopupIcon(false);

    final DefaultActionGroup optionsGroup = new DefaultActionGroup();
    final ActionToolbar optionsToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, optionsGroup, true);
    optionsToolBar.setReservePlaceAutoPopupIcon(false);
    optionsToolBar.setSecondaryActionsTooltip("Options");
    optionsGroup.addAction(new CheckboxAction("Hide for non-layout files") {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return mySettings.getGlobalState().isHideForNonLayoutFiles();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        mySettings.getGlobalState().setHideForNonLayoutFiles(state);
      }
    }).setAsSecondary(true);

    gb.gridx = 0;
    gb.gridy++;
    final JComponent toolbar = myActionToolBar.getComponent();
    final JPanel toolBarWrapper = new JPanel(new BorderLayout());
    toolBarWrapper.add(toolbar, BorderLayout.CENTER);
    toolBarWrapper.setPreferredSize(new Dimension(10, toolbar.getMinimumSize().height));
    toolBarWrapper.setMinimumSize(new Dimension(10, toolbar.getMinimumSize().height));

    final JPanel fullToolbarComponent = new JPanel(new BorderLayout());
    fullToolbarComponent.add(toolBarWrapper, BorderLayout.CENTER);
    fullToolbarComponent.add(optionsToolBar.getComponent(), BorderLayout.EAST);
    myComboPanel.add(fullToolbarComponent, gb);

    gb.fill = GridBagConstraints.HORIZONTAL;
    myThemeCombo = new ComboBox();
    gb.gridx++;
    gb.gridwidth = 2;
    myComboPanel.add(myThemeCombo, gb);

    myContentPanel.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        myPreviewPanel.updateImageSize();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }

      @Override
      public void componentShown(ComponentEvent e) {
      }

      @Override
      public void componentHidden(ComponentEvent e) {
      }
    });

    myScrollPane.getHorizontalScrollBar().setUnitIncrement(5);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(5);

    myDockModeCombo.setModel(new DefaultComboBoxModel(UiMode.values()));
    myDockModeCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((UiMode)value).getLongDisplayValue());
      }
    });
    final ActionListener defaultComboListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveState();
        myToolWindowManager.render();
      }
    };

    myNightCombo.setModel(new DefaultComboBoxModel(NightMode.values()));
    myNightCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((NightMode)value).getLongDisplayValue());
      }
    });

    myTargetCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof IAndroidTarget) {
          final IAndroidTarget target = (IAndroidTarget)value;
          setText(target.getName());
        }
        else {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    myTargetCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateThemes();
        saveState();
        myToolWindowManager.render();
      }
    });

    myThemeCombo.addItemListener(new ItemListener() {
      private ThemeData myPrevThemeData;

      @Override
      public void itemStateChanged(ItemEvent e) {
        final Object item = e.getItem();
        if (item instanceof ThemeData) {
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            myPrevThemeData = (ThemeData)item;
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED && myPrevThemeData != null) {
          myThemeCombo.setSelectedItem(myPrevThemeData);
        }
      }
    });

    myDeviceConfigurationsCombo.addActionListener(defaultComboListener);
    myDockModeCombo.addActionListener(defaultComboListener);
    myNightCombo.addActionListener(defaultComboListener);
    myLocaleCombo.addActionListener(defaultComboListener);
    myThemeCombo.addActionListener(defaultComboListener);

    myDeviceConfigurationsCombo.setMinimumAndPreferredWidth(10);
    myDockModeCombo.setMinimumAndPreferredWidth(10);
    myNightCombo.setMinimumAndPreferredWidth(10);
    myDevicesCombo.setMinimumAndPreferredWidth(10);
    myTargetCombo.setMinimumAndPreferredWidth(10);
    myLocaleCombo.setMinimumAndPreferredWidth(10);
    myThemeCombo.setMinimumAndPreferredWidth(10);

    myDevicesCombo.setMaximumRowCount(20);
    myThemeCombo.setMaximumRowCount(20);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public void setFile(@Nullable PsiFile file) {
    final boolean fileChanged = !Comparing.equal(myFile, file);
    myFile = file;

    final AndroidPlatform newPlatform = getPlatform(file);
    if (newPlatform == null || !newPlatform.equals(myPrevPlatform)) {
      myPrevPlatform = newPlatform;
      if (file != null) {
        updateLocales();
        updateDevicesAndTargets(newPlatform);
        updateThemes();
        if (!myResetFlag) {
          reset();
          myResetFlag = true;
          return;
        }
      }
    }

    if (file != null && fileChanged) {
      myResetFlag = false;
      reset();
      myResetFlag = true;
    }
  }
  
  @Nullable
  private LayoutDevice findDeviceByName(@NotNull String name) {
    for (LayoutDevice device : myDevices) {
      if (device.getName().equals(name)) {
        return device;
      }
    }
    return null;
  }
  
  @Nullable
  private LayoutDeviceConfiguration findDeviceConfigByName(@NotNull String name) {
    for (LayoutDeviceConfiguration configuration : myDeviceConfigurations) {
      if (configuration.getName().equals(name)) {
        return configuration;
      }
    }
    return null;
  }
  
  @Nullable
  private VirtualFile getVirtualFile() {
    return myFile != null ? myFile.getVirtualFile() : null;
  }

  private void saveState() {
    final AndroidLayoutPreviewToolWindowSettings.GlobalState state = mySettings.getGlobalState();

    if (myResetFlag) {
      final LayoutDevice selectedDevice = getSelectedDevice();
      if (selectedDevice != null) {
        state.setDevice(selectedDevice.getName());
      }

      final LayoutDeviceConfiguration deviceConfig = getSelectedDeviceConfiguration();
      final VirtualFile vFile = getVirtualFile();
      if (deviceConfig != null && vFile != null) {
        final LayoutDeviceConfiguration defaultConfig = getDefaultDeviceConfigForFile(vFile);
        final String defaultConfigName = defaultConfig != null ? defaultConfig.getName() : null;
        final String deviceConfigName = deviceConfig.getName();

        if (Comparing.equal(deviceConfigName, defaultConfigName)) {
          mySettings.removeDeviceConfiguration(vFile);
        }
        else {
          mySettings.setDeviceConfiguration(vFile, deviceConfigName);
        }
      }

      final UiMode dockMode = getSelectedDockMode();
      if (dockMode != null) {
        state.setDockMode(dockMode.getResourceValue());
      }

      final NightMode nightMode = getSelectedNightMode();
      if (nightMode != null) {
        state.setNightMode(nightMode.getResourceValue());
      }

      final LocaleData localeData = getSelectedLocaleData();
      if (localeData != null) {
        state.setLocaleLanguage(localeData.getLanguage());
        state.setLocaleRegion(localeData.getRegion());
      }

      final IAndroidTarget target = getSelectedTarget();
      if (target != null) {
        state.setTargetHashString(target.hashString());
      }
    }

    if (myThemesResetFlag) {
      final ThemeData theme = getSelectedTheme();
      if (theme != null) {
        state.setTheme(theme.getName());
      }
    }
  }

  private void reset() {
    final AndroidLayoutPreviewToolWindowSettings.GlobalState state = mySettings.getGlobalState();

    final String savedDeviceName = state.getDevice();
    if (savedDeviceName != null) {
      LayoutDevice savedDevice = null;
      for (LayoutDevice device : myDevices) {
        if (savedDeviceName.equals(device.getName())) {
          savedDevice = device;
          break;
        }
      }
      if (savedDevice != null) {
        myDevicesCombo.setSelectedItem(savedDevice);
      }
    }

    final VirtualFile vFile = getVirtualFile();
    if (vFile != null) {
      final String savedDeviceConfigName = mySettings.getDeviceConfiguration(vFile);

      if (savedDeviceConfigName != null) {
        LayoutDeviceConfiguration savedDeviceConfig = null;
        for (LayoutDeviceConfiguration config : myDeviceConfigurations) {
          if (savedDeviceConfigName.equals(config.getName())) {
            savedDeviceConfig = config;
            break;
          }
        }
        if (savedDeviceConfig != null) {
          myDeviceConfigurationsCombo.setSelectedItem(savedDeviceConfig);
        }
      }
      else {
        final LayoutDeviceConfiguration defaultConfig = getDefaultDeviceConfigForFile(vFile);
        
        if (defaultConfig != null) {
          myDeviceConfigurationsCombo.setSelectedItem(defaultConfig);
        }
      }
    }

    final String savedTargetHashString = state.getTargetHashString();
    if (savedTargetHashString != null) {
      IAndroidTarget savedTarget = null;
      for (IAndroidTarget target : myTargets) {
        if (savedTargetHashString.equals(target.hashString())) {
          savedTarget = target;
          break;
        }
      }
      if (savedTarget != null) {
        myTargetCombo.setSelectedItem(savedTarget);
      }
    }

    final String savedLocaleLanguage = state.getLocaleLanguage();
    final String savedLocaleRegion = state.getLocaleRegion();
    if (savedLocaleLanguage != null || savedLocaleRegion != null) {
      LocaleData savedLocale = null;
      LocaleData savedLocaleCandidate = null;
      for (LocaleData locale : myLocales) {
        if (Comparing.equal(locale.getLanguage(), savedLocaleLanguage)) {
          if (Comparing.equal(locale.getRegion(), savedLocaleRegion)) {
            savedLocale = locale;
            break;
          }
          else if (savedLocaleCandidate == null) {
            savedLocaleCandidate = locale;
          }
        }
      }
      if (savedLocale == null) {
        savedLocale = savedLocaleCandidate;
      }
      if (savedLocale != null) {
        myLocaleCombo.setSelectedItem(savedLocale);
      }
    }

    if (state.getDockMode() != null) {
      final UiMode savedDockMode = UiMode.getEnum(state.getDockMode());
      if (savedDockMode != null) {
        myDockModeCombo.setSelectedItem(savedDockMode);
      }
    }

    if (state.getNightMode() != null) {
      final NightMode savedNightMode = NightMode.getEnum(state.getNightMode());
      if (savedNightMode != null) {
        myNightCombo.setSelectedItem(savedNightMode);
      }
    }
  }

  @Nullable
  private LayoutDeviceConfiguration getDefaultDeviceConfigForFile(@NotNull VirtualFile vFile) {
    final VirtualFile folder = vFile.getParent();

    if (folder == null) {
      return null;
    }
    final String[] folderSegments = folder.getName().split(AndroidConstants.RES_QUALIFIER_SEP);

    if (folderSegments.length == 0) {
      return null;
    }
    final FolderConfiguration config = FolderConfiguration.getConfig(folderSegments);

    if (config != null) {
      for (LayoutDeviceConfiguration deviceConfig : myDeviceConfigurations) {
        if (deviceConfig.getConfiguration().isMatchFor(config)) {
          return deviceConfig;
        }
      }
    }
    return null;
  }

  private void resetThemes(Collection<Object> themes) {
    final String savedThemeName = mySettings.getGlobalState().getTheme();
    if (savedThemeName != null) {
      ThemeData savedTheme = null;
      for (Object o : themes) {
        if (o instanceof ThemeData) {
          final ThemeData theme = (ThemeData)o;
          if (savedThemeName.equals(theme.getName())) {
            savedTheme = theme;
            break;
          }
        }
      }
      if (savedTheme != null) {
        myThemeCombo.setSelectedItem(savedTheme);
      }
    }
  }

  @Override
  public void dispose() {
  }

  @Nullable
  private static AndroidPlatform getPlatform(PsiFile file) {
    if (file == null) {
      return null;
    }

    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) {
      return null;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        return additionalData.getAndroidPlatform();
      }
    }
    return null;
  }

  public void setErrorMessage(FixableIssueMessage errorMessage) {
    myPreviewPanel.setErrorMessage(errorMessage);
  }

  public void setWarnMessage(List<FixableIssueMessage> warnMessages) {
    myPreviewPanel.setWarnMessages(warnMessages);
  }

  public void setImage(@Nullable BufferedImage image, @NotNull String fileName) {
    myPreviewPanel.setImage(image, fileName);
  }

  @NotNull
  public AndroidLayoutPreviewPanel getPreviewPanel() {
    return myPreviewPanel;
  }

  public void updatePreviewPanel() {
    myPreviewPanel.update();
  }

  public void updateDevicesAndTargets(@Nullable AndroidPlatform platform) {
    final AndroidSdkData sdkData = platform != null ? platform.getSdkData() : null;
    final LayoutDevice selectedDevice = getSelectedDevice();
    final String selectedDeviceName = selectedDevice != null ? selectedDevice.getName() : null;

    final List<LayoutDevice> devices;
    if (sdkData != null) {
      myLayoutDeviceManager.loadDevices(sdkData);
      devices = myLayoutDeviceManager.getCombinedList();
    }
    else {
      devices = Collections.emptyList();
    }

    LayoutDevice newSelectedDevice = devices.size() > 0 ? devices.get(0) : null;
    if (selectedDeviceName != null) {
      for (LayoutDevice device : devices) {
        if (selectedDeviceName.equals(device.getName())) {
          newSelectedDevice = device;
        }
      }
    }
    if (newSelectedDevice == null && devices.size() > 0) {
      newSelectedDevice = devices.get(0);
    }
    myDevices = devices;
    final List<Object> devicesCopy = new ArrayList<Object>(devices);
    devicesCopy.add(CUSTOM_DEVICE_STRING);
    myDevicesCombo.setModel(new CollectionComboBoxModel(devicesCopy, newSelectedDevice));

    if (newSelectedDevice != null) {
      updateDeviceConfigurations(newSelectedDevice);
    }

    final IAndroidTarget oldSelectedTarget = (IAndroidTarget)myTargetCombo.getSelectedItem();
    final String selectedTargetHashString = oldSelectedTarget != null ? oldSelectedTarget.hashString() : null;
    IAndroidTarget newSelectedTarget = null;

    final List<IAndroidTarget> targets;
    if (sdkData != null) {
      targets = new ArrayList<IAndroidTarget>();
      for (IAndroidTarget target : sdkData.getTargets()) {
        if (target.isPlatform()) {
          if (target.hashString().equals(selectedTargetHashString)) {
            newSelectedTarget = target;
          }
          targets.add(target);
        }
      }
    }
    else {
      targets = Collections.emptyList();
    }
    
    if (newSelectedTarget == null) {
      IAndroidTarget targetFromModule = platform != null ? platform.getTarget() : null;
      
      if (targetFromModule != null) {
        String modulePlatformHash = null;
        
        if (targetFromModule.isPlatform()) {
          modulePlatformHash = targetFromModule.hashString();
        }
        else {
          final IAndroidTarget parent = targetFromModule.getParent();
          if (parent != null) {
            modulePlatformHash = parent.hashString();
          }
        }
        
        if (modulePlatformHash != null) {
          targetFromModule = sdkData.findTargetByHashString(modulePlatformHash);
          if (targetFromModule != null && targets.indexOf(targetFromModule) >= 0) {
            newSelectedTarget = targetFromModule;
          }
        }
      }
    }

    if (newSelectedTarget == null && targets.size() > 0) {
      newSelectedTarget = targets.get(0);
    }
    myTargets = targets;
    myTargetCombo.setModel(new CollectionComboBoxModel(targets, newSelectedTarget));
  }

  private void updateDeviceConfigurations(@Nullable LayoutDevice device) {
    final LayoutDeviceConfiguration selectedConfiguration = getSelectedDeviceConfiguration();
    final String selectedConfigurationName = selectedConfiguration != null
                                             ? selectedConfiguration.getName()
                                             : null;
    final List<LayoutDeviceConfiguration> configurations = device != null
                                                           ? device.getConfigurations()
                                                           : Collections.<LayoutDeviceConfiguration>emptyList();

    LayoutDeviceConfiguration newSelectedConfiguration = configurations.size() > 0
                                                         ? configurations.get(0)
                                                         : null;
    if (selectedConfigurationName != null) {
      for (LayoutDeviceConfiguration configuration : configurations) {
        if (selectedConfigurationName.equals(configuration.getName())) {
          newSelectedConfiguration = configuration;
        }
      }
    }
    if (newSelectedConfiguration == null) {
      newSelectedConfiguration = configurations.get(0);
    }

    myDeviceConfigurations = configurations;
    myDeviceConfigurationsCombo.setModel(new CollectionComboBoxModel(configurations, newSelectedConfiguration));
  }

  public void updateLocales() {
    if (myFile == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(myFile);
    if (facet == null) {
      return;
    }
    final LocaleData oldSelectedLocale = (LocaleData)myLocaleCombo.getSelectedItem();
    final Map<String, Set<String>> language2Regions = new HashMap<String, Set<String>>();
    final VirtualFile[] resourceDirs = facet.getLocalResourceManager().getAllResourceDirs();

    for (VirtualFile resourceDir : resourceDirs) {
      for (VirtualFile child : resourceDir.getChildren()) {
        if (child.isDirectory()) {
          final String resDirName = child.getName();
          final String[] segments = resDirName.split(AndroidConstants.RES_QUALIFIER_SEP);

          final List<String> languageQualifiers = new ArrayList<String>();
          final List<String> regionQualifiers = new ArrayList<String>();

          for (String segment : segments) {
            final LanguageQualifier languageQualifier = LanguageQualifier.getQualifier(segment);
            if (languageQualifier != null) {
              languageQualifiers.add(languageQualifier.getValue());
            }
            final RegionQualifier regionQualifier = RegionQualifier.getQualifier(segment);
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
    final List<LocaleData> locales = new ArrayList<LocaleData>();

    for (String language : language2Regions.keySet()) {
      final Set<String> regions = language2Regions.get(language);

      for (String region : regions) {
        final String presentation = String.format("%1$s / %2$s", language, region);
        locales.add(new LocaleData(language, region, presentation));
      }
      final String presentation = regions.size() > 0
                                  ? String.format("%1$s / Other", language)
                                  : String.format("%1$s / Any", language);
      locales.add(new LocaleData(language, null, presentation));
    }
    locales.add(new LocaleData(null, null, language2Regions.size() > 0 ? "Other locale" : "Any locale"));

    LocaleData newSelectedLocale = null;
    for (LocaleData locale : locales) {
      if (locale.equals(oldSelectedLocale)) {
        newSelectedLocale = locale;
      }
    }

    if (newSelectedLocale == null) {
      final Locale defaultLocale = Locale.getDefault();
      if (defaultLocale != null) {
        for (LocaleData locale : locales) {
          if (locale.equals(new LocaleData(defaultLocale.getLanguage(), defaultLocale.getCountry(), ""))) {
            newSelectedLocale = locale;
          }
        }
      }
    }

    Collections.sort(locales, new Comparator<LocaleData>() {
      @Override
      public int compare(LocaleData l1, LocaleData l2) {
        return l1.toString().compareTo(l2.toString());
      }
    });

    if (newSelectedLocale == null && locales.size() > 0) {
      newSelectedLocale = locales.get(0);
    }

    myLocales = locales;
    myLocaleCombo.setModel(new CollectionComboBoxModel(locales, newSelectedLocale));
  }

  public void updateThemes() {
    if (myFile == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(myFile);
    if (facet == null) {
      return;
    }

    final List<Object> themes = new ArrayList<Object>();
    final HashSet<ThemeData> addedThemes = new HashSet<ThemeData>();
    final ArrayList<ThemeData> projectThemes = new ArrayList<ThemeData>();

    collectThemesFromManifest(facet, projectThemes, addedThemes, true);
    collectProjectThemes(facet, projectThemes, addedThemes);

    if (projectThemes.size() > 0) {
      themes.add("Project themes");
      themes.addAll(projectThemes);
    }

    final Module module = facet.getModule();
    AndroidTargetData targetData = null;
    final AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    if (androidPlatform != null) {
      IAndroidTarget target = getSelectedTarget();
      if (target == null) {
        target = androidPlatform.getTarget();
      }
      targetData = androidPlatform.getSdkData().getTargetData(target);
    }

    if (targetData == null || targetData.areThemesCached()) {
      collectFrameworkThemes(themes, facet, targetData, addedThemes);
      doApplyThemes(themes, addedThemes);
    }
    else {
      final AndroidTargetData finalTargetData = targetData;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {

          ProgressManager.getInstance().runProcess(new Runnable() {
            @Override
            public void run() {
              collectFrameworkThemes(themes, facet, finalTargetData, addedThemes);
            }
          }, new AndroidPreviewProgressIndicator(AndroidLayoutPreviewToolWindowForm.this, 0));

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              doApplyThemes(themes, addedThemes);
              myToolWindowManager.render();
            }
          });
        }
      });
    }
  }

  private void collectFrameworkThemes(List<Object> themes,
                                      AndroidFacet facet,
                                      @Nullable AndroidTargetData targetData,
                                      Set<ThemeData> addedThemes) {
    final List<ThemeData> frameworkThemes = new ArrayList<ThemeData>();
    collectThemesFromManifest(facet, frameworkThemes, addedThemes, false);
    if (targetData != null) {
      doCollectFrameworkThemes(facet, targetData, frameworkThemes, addedThemes);
    }
    if (frameworkThemes.size() > 0) {
      themes.add("Framework themes");
      themes.addAll(frameworkThemes);
    }
  }

  private void doApplyThemes(List<Object> themes, final Set<ThemeData> themesSet) {
    final ThemeData oldSelection = (ThemeData)myThemeCombo.getSelectedItem();

    ThemeData selection = null;
    for (Object o : themes) {
      if (o instanceof ThemeData && o.equals(oldSelection)) {
        selection = (ThemeData)o;
      }
    }
    if (selection == null) {
      for (Object o : themes) {
        if (o instanceof ThemeData) {
          selection = (ThemeData)o;
          break;
        }
      }
    }
    myThemeCombo.setModel(new CollectionComboBoxModel(themes, selection));

    myThemeCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ThemeData) {
          final ThemeData themeData = (ThemeData)value;
          if (index == -1 && !themeData.isProjectTheme() && themesSet.contains(new ThemeData(themeData.getName(), true))) {
            setText(value.toString() + " (framework)");
          }
          else if (index != -1) {
            setText("      " + value.toString());
          }
        }
        else if (index == -1) {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    if (myFile != null && !myThemesResetFlag) {
      resetThemes(themes);
      myThemesResetFlag = true;
    }

    saveState();
  }

  private static void doCollectFrameworkThemes(AndroidFacet facet,
                                               @NotNull AndroidTargetData targetData,
                                               List<ThemeData> themes,
                                               Set<ThemeData> addedThemes) {
    final List<String> frameworkThemeNames = new ArrayList<String>(targetData.getThemes(facet));
    Collections.sort(frameworkThemeNames);
    for (String themeName : frameworkThemeNames) {
      final ThemeData themeData = new ThemeData(themeName, false);
      if (addedThemes.add(themeData)) {
        themes.add(themeData);
      }
    }
  }

  private void collectThemesFromManifest(final AndroidFacet facet,
                                         final List<ThemeData> resultList,
                                         final Set<ThemeData> addedThemes,
                                         final boolean fromProject) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        doCollectThemesFromManifest(facet, resultList, addedThemes, fromProject);
      }
    });
  }

  private void doCollectThemesFromManifest(AndroidFacet facet,
                                           List<ThemeData> resultList,
                                           Set<ThemeData> addedThemes,
                                           boolean fromProject) {
    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final Application application = manifest.getApplication();
    if (application == null) {
      return;
    }

    final List<ThemeData> activityThemesList = new ArrayList<ThemeData>();

    final XmlTag applicationTag = application.getXmlTag();
    ThemeData preferredTheme = null;
    if (applicationTag != null) {
      final String applicationThemeRef = applicationTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
      if (applicationThemeRef != null) {
        preferredTheme = getThemeByRef(applicationThemeRef);
      }
    }

    if (preferredTheme == null) {
      final AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
      final IAndroidTarget target = platform != null ? platform.getTarget() : null;
      final IAndroidTarget renderingTarget = getSelectedTarget();
      final LayoutDeviceConfiguration configuration = getSelectedDeviceConfiguration();

      final ScreenSizeQualifier screenSizeQualifier = configuration != null
                                                      ? configuration.getConfiguration().getScreenSizeQualifier()
                                                      : null;
      final ScreenSize screenSize = screenSizeQualifier != null ? screenSizeQualifier.getValue() : null;
      preferredTheme = getThemeByRef(getDefaultTheme(target, renderingTarget, screenSize));
    }

    if (!addedThemes.contains(preferredTheme) && fromProject == preferredTheme.isProjectTheme()) {
      addedThemes.add(preferredTheme);
      resultList.add(preferredTheme);
    }

    for (Activity activity : application.getActivities()) {
      final XmlTag activityTag = activity.getXmlTag();
      if (activityTag != null) {
        final String activityThemeRef = activityTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
        if (activityThemeRef != null) {
          final ThemeData activityTheme = getThemeByRef(activityThemeRef);
          if (!addedThemes.contains(activityTheme) && fromProject == activityTheme.isProjectTheme()) {
            addedThemes.add(activityTheme);
            activityThemesList.add(activityTheme);
          }
        }
      }
    }

    Collections.sort(activityThemesList);
    resultList.addAll(activityThemesList);
  }

  @NotNull
  private static String getDefaultTheme(IAndroidTarget target,
                                        IAndroidTarget renderingTarget,
                                        ScreenSize screenSize) {
    final int targetApiLevel = target != null ? target.getVersion().getApiLevel() : 0;

    final int renderingTargetApiLevel = renderingTarget != null
                                        ? renderingTarget.getVersion().getApiLevel()
                                        : targetApiLevel;

    return targetApiLevel >= 11 && renderingTargetApiLevel >= 11 && screenSize == ScreenSize.XLARGE
           ? ResourceResolver.PREFIX_ANDROID_STYLE + "Theme.Holo"
           : ResourceResolver.PREFIX_ANDROID_STYLE + "Theme";
  }

  private static void collectProjectThemes(AndroidFacet facet, Collection<ThemeData> resultList, Set<ThemeData> addedThemes) {
    final List<ThemeData> newThemes = new ArrayList<ThemeData>();
    final Map<String, ResourceElement> styleMap = buildStyleMap(facet);

    for (ResourceElement style : styleMap.values()) {
      if (isTheme(style, styleMap, new HashSet<ResourceElement>())) {
        final String themeName = style.getName().getValue();
        if (themeName != null) {
          final ThemeData theme = new ThemeData(themeName, true);
          if (addedThemes.add(theme)) {
            newThemes.add(theme);
          }
        }
      }
    }

    Collections.sort(newThemes);
    resultList.addAll(newThemes);
  }

  private static Map<String, ResourceElement> buildStyleMap(AndroidFacet facet) {
    final Map<String, ResourceElement> result = new HashMap<String, ResourceElement>();
    final List<ResourceElement> styles = facet.getLocalResourceManager().getValueResources(ResourceType.STYLE.getName());
    for (ResourceElement style : styles) {
      final String styleName = style.getName().getValue();
      if (styleName != null) {
        result.put(styleName, style);
      }
    }
    return result;
  }

  private static boolean isTheme(ResourceElement resElement, Map<String, ResourceElement> styleMap, Set<ResourceElement> visitedElements) {
    if (!visitedElements.add(resElement)) {
      return false;
    }

    if (!(resElement instanceof Style)) {
      return false;
    }

    final String styleName = resElement.getName().getValue();
    if (styleName == null) {
      return false;
    }

    final ResourceValue parentStyleRef = ((Style)resElement).getParentStyle().getValue();
    String parentStyleName = null;
    boolean frameworkStyle = false;

    if (parentStyleRef != null) {
      final String s = parentStyleRef.getResourceName();
      if (s != null) {
        parentStyleName = s;
        frameworkStyle = AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(parentStyleRef.getPackage());
      }
    }

    if (parentStyleRef == null) {
      final int index = styleName.indexOf('.');
      if (index >= 0) {
        parentStyleName = styleName.substring(0, index);
      }
    }

    if (parentStyleRef != null) {
      if (frameworkStyle) {
        return parentStyleName.equals("Theme") || parentStyleName.startsWith("Theme.");
      }
      else {
        final ResourceElement parentStyle = styleMap.get(parentStyleName);
        if (parentStyle != null) {
          return isTheme(parentStyle, styleMap, visitedElements);
        }
      }
    }

    return false;
  }

  @NotNull
  private static ThemeData getThemeByRef(@NotNull String themeRef) {
    final boolean isProjectTheme = !themeRef.startsWith(ResourceResolver.PREFIX_ANDROID_STYLE);
    if (themeRef.startsWith(ResourceResolver.PREFIX_STYLE)) {
      themeRef = themeRef.substring(ResourceResolver.PREFIX_STYLE.length());
    }
    else if (themeRef.startsWith(ResourceResolver.PREFIX_ANDROID_STYLE)) {
      themeRef = themeRef.substring(ResourceResolver.PREFIX_ANDROID_STYLE.length());
    }
    return new ThemeData(themeRef, isProjectTheme);
  }

  @Nullable
  public LayoutDeviceConfiguration getSelectedDeviceConfiguration() {
    return (LayoutDeviceConfiguration)myDeviceConfigurationsCombo.getSelectedItem();
  }

  @Nullable
  public LayoutDevice getSelectedDevice() {
    final Object selectedObj = myDevicesCombo.getSelectedItem();
    if (selectedObj instanceof LayoutDevice) {
      return (LayoutDevice)selectedObj;
    }
    return null;
  }

  @Nullable
  public UiMode getSelectedDockMode() {
    return (UiMode)myDockModeCombo.getSelectedItem();
  }

  @Nullable
  public NightMode getSelectedNightMode() {
    return (NightMode)myNightCombo.getSelectedItem();
  }

  @Nullable
  public IAndroidTarget getSelectedTarget() {
    return (IAndroidTarget)myTargetCombo.getSelectedItem();
  }

  @Nullable
  public LocaleData getSelectedLocaleData() {
    return (LocaleData)myLocaleCombo.getSelectedItem();
  }

  @Nullable
  public ThemeData getSelectedTheme() {
    final Object item = myThemeCombo.getSelectedItem();
    if (item instanceof ThemeData) {
      return (ThemeData)item;
    }
    return null;
  }

  private class MyZoomInAction extends AnAction {
    MyZoomInAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.in.action.text"), null, AndroidIcons.ZoomIn);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomIn();
      myActionToolBar.updateActionsImmediately();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myPreviewPanel.canZoomIn());
    }
  }

  private class MyZoomOutAction extends AnAction {
    MyZoomOutAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.out.action.text"), null, AndroidIcons.ZoomOut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomOut();
      myActionToolBar.updateActionsImmediately();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myPreviewPanel.isZoomToFit() && myPreviewPanel.canZoomOut());
    }
  }

  private class MyZoomActualAction extends AnAction {
    MyZoomActualAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.actual.action.text"), null, AndroidIcons.ZoomActual);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomActual();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class MyZoomToFitAction extends ToggleAction {
    MyZoomToFitAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.to.fit.action.text"), null, AndroidIcons.ZoomFit);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myPreviewPanel.isZoomToFit();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myPreviewPanel.setZoomToFit(state);
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class MyRefreshAction extends AnAction {
    MyRefreshAction() {
      super(AndroidBundle.message("android.layout.preview.refresh.action.text"), null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myToolWindowManager.render();
    }
  }
}
