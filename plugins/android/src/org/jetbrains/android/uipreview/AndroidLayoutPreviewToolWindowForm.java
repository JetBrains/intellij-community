package org.jetbrains.android.uipreview;

import com.android.AndroidConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.resources.DockMode;
import com.android.resources.NightMode;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowForm implements Disposable {
  private static final Icon ZOOM_TO_FIT_ICON = IconLoader.getIcon("/icons/zoomFit.png");
  private static final Icon ZOOM_IN_ICON = IconLoader.getIcon("/icons/zoomIn.png");
  private static final Icon ZOOM_OUT_ICON = IconLoader.getIcon("/icons/zoomOut.png");
  private static final Icon ZOOM_ACTUAL_ICON = IconLoader.getIcon("/icons/zoomActual.png");

  private JPanel myContentPanel;
  private AndroidLayoutPreviewPanel myPreviewPanel;
  private ComboBox myDevicesCombo;
  private ComboBox myDeviceConfigurationsCombo;
  private JBScrollPane myScrollPane;
  private ComboBox myDockModeCombo;
  private ComboBox myNightCombo;
  private ComboBox myTargetCombo;
  private ComboBox myLocaleCombo;
  private ComboBox myThemeCombo;
  private JPanel myComboPanel;

  private PsiFile myFile;

  private AndroidPlatform myPrevPlatform = null;
  private LayoutDeviceManager myLayoutDeviceManager = new LayoutDeviceManager();
  private final AndroidLayoutPreviewToolWindowManager myToolWindowManager;
  private final ActionToolbar myActionToolBar;

  public AndroidLayoutPreviewToolWindowForm(AndroidLayoutPreviewToolWindowManager toolWindowManager) {
    myToolWindowManager = toolWindowManager;

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

    myDevicesCombo.setRenderer(new ListCellRendererWrapper(myDevicesCombo.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LayoutDevice) {
          final LayoutDevice device = (LayoutDevice)value;
          setText(device.getName());
        }
        else {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });

    myDeviceConfigurationsCombo.setRenderer(new ListCellRendererWrapper(myDevicesCombo.getRenderer()) {
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
        final LayoutDevice newSelectedDevice = getSelectedDevice();
        updateDeviceConfigurations(newSelectedDevice);
        myToolWindowManager.render();
      }
    });

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyZoomToFitAction());
    actionGroup.add(new MyZoomActualAction());
    actionGroup.add(new MyZoomInAction());
    actionGroup.add(new MyZoomOutAction());
    myActionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myActionToolBar.setReservePlaceAutoPopupIcon(false);

    gb.gridx = 0;
    gb.gridy++;
    final JComponent toolbar = myActionToolBar.getComponent();
    final JPanel toolBarWrapper = new JPanel(new BorderLayout());
    toolBarWrapper.add(toolbar, BorderLayout.CENTER);
    toolBarWrapper.setPreferredSize(new Dimension(10, toolbar.getPreferredSize().height));
    toolBarWrapper.setMinimumSize(new Dimension(10, toolbar.getMinimumSize().height));
    myComboPanel.add(toolBarWrapper, gb);

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

    myDockModeCombo.setModel(new DefaultComboBoxModel(DockMode.values()));
    myDockModeCombo.setRenderer(new ListCellRendererWrapper(myDockModeCombo.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((DockMode)value).getLongDisplayValue());
      }
    });
    final ActionListener renderingListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myToolWindowManager.render();
      }
    };

    myNightCombo.setModel(new DefaultComboBoxModel(NightMode.values()));
    myNightCombo.setRenderer(new ListCellRendererWrapper(myNightCombo.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((NightMode)value).getLongDisplayValue());
      }
    });

    myTargetCombo.setRenderer(new ListCellRendererWrapper(myDevicesCombo.getRenderer()) {
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
        myToolWindowManager.render();
      }
    });

    myDeviceConfigurationsCombo.addActionListener(renderingListener);
    myDockModeCombo.addActionListener(renderingListener);
    myNightCombo.addActionListener(renderingListener);
    myLocaleCombo.addActionListener(renderingListener);
    myThemeCombo.addActionListener(renderingListener);

    myDeviceConfigurationsCombo.setMinimumAndPreferredWidth(10);
    myDockModeCombo.setMinimumAndPreferredWidth(10);
    myNightCombo.setMinimumAndPreferredWidth(10);
    myDevicesCombo.setMinimumAndPreferredWidth(10);
    myTargetCombo.setMinimumAndPreferredWidth(10);
    myLocaleCombo.setMinimumAndPreferredWidth(10);
    myThemeCombo.setMinimumAndPreferredWidth(10);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public void setFile(@Nullable PsiFile file) {
    myFile = file;

    final AndroidPlatform newPlatform = getNewPlatform(file);
    if (newPlatform == null || !newPlatform.equals(myPrevPlatform)) {
      myPrevPlatform = newPlatform;
      if (file != null) {
        updateLocales();
        updateDevicesAndTargets(newPlatform);
        updateThemes();
      }
    }
  }

  @Override
  public void dispose() {
  }

  @Nullable
  private static AndroidPlatform getNewPlatform(PsiFile file) {
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

  public void setErrorMessage(RenderingErrorMessage errorMessage) {
    myPreviewPanel.setErrorMessage(errorMessage);
  }

  public void setImage(BufferedImage image) {
    myPreviewPanel.setImage(image);
  }

  public void updatePreviewPanel() {
    myPreviewPanel.update();
  }

  public void updateDevicesAndTargets(@Nullable AndroidPlatform platform) {
    final AndroidSdk sdkObject = platform != null ? platform.getSdk() : null;
    final LayoutDevice selectedDevice = getSelectedDevice();
    final String selectedDeviceName = selectedDevice != null ? selectedDevice.getName() : null;

    final List<LayoutDevice> devices;
    if (sdkObject != null) {
      myLayoutDeviceManager.loadDevices(sdkObject);
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
    myDevicesCombo.setModel(new CollectionComboBoxModel(devices, newSelectedDevice));

    if (newSelectedDevice != null) {
      updateDeviceConfigurations(newSelectedDevice);
    }

    final IAndroidTarget oldSelectedTarget = (IAndroidTarget)myTargetCombo.getSelectedItem();
    final String selectedTargetHashString = oldSelectedTarget != null ? oldSelectedTarget.hashString() : null;
    IAndroidTarget newSelectedTarget = platform != null ? platform.getTarget() : null;

    final List<IAndroidTarget> targets;
    if (sdkObject != null) {
      targets = new ArrayList<IAndroidTarget>();
      for (IAndroidTarget target : sdkObject.getTargets()) {
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

    if (newSelectedTarget == null && targets.size() > 0) {
      newSelectedTarget = targets.get(0);
    }
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
    myDeviceConfigurationsCombo.setModel(new CollectionComboBoxModel(configurations, newSelectedConfiguration));
  }

  public void updateLocales() {
    if (myFile == null) {
      return;
    }

    final LocaleData oldSelectedLocale = (LocaleData)myLocaleCombo.getSelectedItem();
    myLocaleCombo.setModel(new DefaultComboBoxModel());

    final AndroidFacet facet = AndroidFacet.getInstance(myFile);
    if (facet == null) {
      return;
    }

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

    final List<ThemeData> themes = new ArrayList<ThemeData>();
    final HashSet<ThemeData> addedThemes = new HashSet<ThemeData>();
    collectThemesFromManifest(facet, themes, addedThemes);
    collectProjectThemes(facet, themes, addedThemes);

    final Module module = facet.getModule();
    final AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    if (androidPlatform != null) {
      IAndroidTarget target = getSelectedTarget();
      if (target == null) {
        target = androidPlatform.getTarget();
      }
      final AndroidTargetData targetData = androidPlatform.getSdk().getTargetData(target);
      if (targetData != null) {
        if (targetData.areThemesCached()) {
          collectFrameworkThemes(module, targetData, themes);
          doApplyThemes(themes);
        }
        else {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              collectFrameworkThemes(module, targetData, themes);
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  doApplyThemes(themes);
                  myToolWindowManager.render();
                }
              });
            }
          });
        }
      }
    }
  }

  private void doApplyThemes(List<ThemeData> themes) {
    final ThemeData oldSelection = (ThemeData)myThemeCombo.getSelectedItem();

    ThemeData selection = null;
    for (ThemeData theme : themes) {
      if (theme.equals(oldSelection)) {
        selection = theme;
      }
    }
    if (selection == null && themes.size() > 0) {
      selection = themes.get(0);
    }
    myThemeCombo.setModel(new CollectionComboBoxModel(themes, selection));
  }

  private static void collectFrameworkThemes(Module module, AndroidTargetData targetData, List<ThemeData> themes) {
    final List<String> frameworkThemeNames = new ArrayList<String>(targetData.getThemes(module));
    Collections.sort(frameworkThemeNames);
    for (String themeName : frameworkThemeNames) {
      themes.add(new ThemeData(themeName, false));
    }
  }

  private static void collectThemesFromManifest(AndroidFacet facet, List<ThemeData> resultList, Set<ThemeData> addedThemes) {
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
    ThemeData applicationTheme = null;
    if (applicationTag != null) {
      final String applicationThemeRef = applicationTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
      if (applicationThemeRef != null) {
        applicationTheme = getThemeByRef(applicationThemeRef);
      }
    }

    for (Activity activity : application.getActivities()) {
      final XmlTag activityTag = activity.getXmlTag();
      if (activityTag != null) {
        final String activityThemeRef = activityTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
        if (activityThemeRef != null) {
          final ThemeData activityTheme = getThemeByRef(activityThemeRef);
          if (addedThemes.add(activityTheme)) {
            activityThemesList.add(activityTheme);
          }
        }
      }
    }

    if (applicationTheme != null) {
      if (addedThemes.add(applicationTheme)) {
        resultList.add(applicationTheme);
      }
    }
    Collections.sort(activityThemesList);
    resultList.addAll(activityThemesList);
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
    if (themeRef.startsWith(ResourceResolver.PREFIX_STYLE)) {
      themeRef = themeRef.substring(ResourceResolver.PREFIX_STYLE.length());
    }
    else if (themeRef.startsWith(ResourceResolver.PREFIX_ANDROID_STYLE)) {
      themeRef = themeRef.substring(ResourceResolver.PREFIX_ANDROID_STYLE.length());
    }
    boolean isProjectTheme = !themeRef.startsWith(ResourceResolver.PREFIX_ANDROID_STYLE);
    return new ThemeData(themeRef, isProjectTheme);
  }

  public LayoutDeviceConfiguration getSelectedDeviceConfiguration() {
    return (LayoutDeviceConfiguration)myDeviceConfigurationsCombo.getSelectedItem();
  }

  public LayoutDevice getSelectedDevice() {
    return (LayoutDevice)myDevicesCombo.getSelectedItem();
  }

  public DockMode getSelectedDockMode() {
    return (DockMode)myDockModeCombo.getSelectedItem();
  }

  public NightMode getSelectedNightMode() {
    return (NightMode)myNightCombo.getSelectedItem();
  }

  public IAndroidTarget getSelectedTarget() {
    return (IAndroidTarget)myTargetCombo.getSelectedItem();
  }

  public LocaleData getSelectedLocaleData() {
    return (LocaleData)myLocaleCombo.getSelectedItem();
  }

  public ThemeData getSelectedTheme() {
    return (ThemeData)myThemeCombo.getSelectedItem();
  }

  private class MyZoomInAction extends AnAction {
    MyZoomInAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.in.action.text"), null, ZOOM_IN_ICON);
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
      super(AndroidBundle.message("android.layout.preview.zoom.out.action.text"), null, ZOOM_OUT_ICON);
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
      super(AndroidBundle.message("android.layout.preview.zoom.actual.action.text"), null, ZOOM_ACTUAL_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPreviewPanel.zoomActual();
      myActionToolBar.updateActionsImmediately();
    }
  }

  private class MyZoomToFitAction extends ToggleAction {
    MyZoomToFitAction() {
      super(AndroidBundle.message("android.layout.preview.zoom.to.fit.action.text"), null, ZOOM_TO_FIT_ICON);
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
}
