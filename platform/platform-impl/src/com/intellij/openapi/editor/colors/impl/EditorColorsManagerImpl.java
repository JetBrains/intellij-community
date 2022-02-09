// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.configurationStore.BundledSchemeEP;
import com.intellij.configurationStore.LazySchemeProcessor;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.configurationStore.SchemeExtensionProvider;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ComponentTreeEventDispatcher;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

@State(
  name = "EditorColorsManagerImpl",
  storages = @Storage("colors.scheme.xml"),
  additionalExportDirectory = EditorColorsManagerImpl.FILE_SPEC,
  category = SettingsCategory.UI
)
@ApiStatus.Internal
public final class EditorColorsManagerImpl extends EditorColorsManager implements PersistentStateComponent<EditorColorsManagerImpl.State> {
  public static final ExtensionPointName<AdditionalTextAttributesEP> ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME = new ExtensionPointName<>("com.intellij.additionalTextAttributes");

  private static final Logger LOG = Logger.getInstance(EditorColorsManagerImpl.class);
  private static final ExtensionPointName<BundledSchemeEP> BUNDLED_EP_NAME = new ExtensionPointName<>("com.intellij.bundledColorScheme");

  private final ComponentTreeEventDispatcher<EditorColorsListener> myTreeDispatcher = ComponentTreeEventDispatcher.create(EditorColorsListener.class);

  private final SchemeManager<EditorColorsScheme> mySchemeManager;
  public static final String FILE_SPEC = "colors";

  private State myState = new State();
  private boolean themeIsCustomized;

  public EditorColorsManagerImpl() {
    this(SchemeManagerFactory.getInstance());
  }

  @NonInjectable
  public EditorColorsManagerImpl(@NotNull SchemeManagerFactory schemeManagerFactory) {
    Map<String, List<AdditionalTextAttributesEP>> additionalTextAttributes = collectAdditionalTextAttributesEPs();
    mySchemeManager = schemeManagerFactory.create(FILE_SPEC, new EditorColorSchemeProcessor(additionalTextAttributes));
    initDefaultSchemes();
    loadBundledSchemes();
    loadSchemesFromThemes();
    mySchemeManager.loadSchemes();
    loadRemainAdditionalTextAttributes(additionalTextAttributes);

    initEditableDefaultSchemesCopies();
    initEditableBundledSchemesCopies();
    resolveLinksToBundledSchemes();

    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        reloadKeepingActiveScheme();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        reloadKeepingActiveScheme();
      }
    });
  }

  private final class EditorColorSchemeProcessor extends LazySchemeProcessor<EditorColorsScheme, EditorColorsSchemeImpl>
    implements SchemeExtensionProvider {
    private final Map<String, List<AdditionalTextAttributesEP>> additionalTextAttributes;

    EditorColorSchemeProcessor(@NotNull Map<String, List<AdditionalTextAttributesEP>> additionalTextAttributes) {
      this.additionalTextAttributes = additionalTextAttributes;
    }

    @Override
    public @NotNull EditorColorsSchemeImpl createScheme(@NotNull SchemeDataHolder<? super EditorColorsSchemeImpl> dataHolder,
                                                        @NotNull String name,
                                                        @NotNull Function<? super String, String> attributeProvider,
                                                        boolean isBundled) {
      EditorColorsSchemeImpl scheme = isBundled ? new BundledScheme() : new EditorColorsSchemeImpl(null);
      // todo be lazy
      scheme.readExternal(dataHolder.read());
      // we don't need to update digest for bundled scheme because
      // 1) it can be computed on demand later (because bundled scheme is not mutable)
      // 2) in the future user copy of bundled scheme will use bundled scheme as parent (not as full copy)
      if (isBundled ||
          (ApplicationManager.getApplication().isUnitTestMode() &&
           Boolean.parseBoolean(scheme.getMetaProperties().getProperty("forceOptimize")))) {
        if (scheme.myParentScheme instanceof AbstractColorsScheme) {
          Collection<AdditionalTextAttributesEP> attributesEPs = additionalTextAttributes.remove(scheme.myParentScheme.getName());
          if (attributesEPs != null && !attributesEPs.isEmpty()) {
            loadAdditionalTextAttributesForScheme((AbstractColorsScheme)scheme.myParentScheme, attributesEPs);
          }
        }

        scheme.optimizeAttributeMap();
      }
      return scheme;
    }

    @Override
    public @NotNull SchemeState getState(@NotNull EditorColorsScheme scheme) {
      return scheme instanceof ReadOnlyColorsScheme ? SchemeState.NON_PERSISTENT : SchemeState.POSSIBLY_CHANGED;
    }

    @Override
    public void onCurrentSchemeSwitched(@Nullable EditorColorsScheme oldScheme,
                                        @Nullable EditorColorsScheme newScheme,
                                        boolean processChangeSynchronously) {
      if (processChangeSynchronously) {
        handleCurrentSchemeSwitched(newScheme);
      }
      else {
        // don't do heavy operations right away
        ApplicationManager.getApplication().invokeLater(() -> {
          handleCurrentSchemeSwitched(newScheme);
        });
      }
    }

    private void handleCurrentSchemeSwitched(@Nullable EditorColorsScheme newScheme) {
      LafManager.getInstance().updateUI();
      schemeChangedOrSwitched(newScheme);
    }

    @Override
    public @NotNull @NonNls String getSchemeExtension() {
      return COLOR_SCHEME_FILE_EXTENSION;
    }

    @Override
    public boolean isSchemeEqualToBundled(@NotNull EditorColorsSchemeImpl scheme) {
      if (!scheme.getName().startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
        return false;
      }

      AbstractColorsScheme bundledScheme =
        (AbstractColorsScheme)mySchemeManager.findSchemeByName(scheme.getName().substring(Scheme.EDITABLE_COPY_PREFIX.length()));
      if (bundledScheme == null) {
        return false;
      }

      return scheme.settingsEqual(bundledScheme);
    }

    @Override
    public void reloaded(@NotNull SchemeManager<EditorColorsScheme> schemeManager,
                         @NotNull Collection<? extends EditorColorsScheme> schemes) {
      loadBundledSchemes();
      loadSchemesFromThemes();
      initEditableDefaultSchemesCopies();
      initEditableBundledSchemesCopies();
      ApplicationManager.getApplication().getMessageBus().syncPublisher(EditorColorsManagerListener.TOPIC).schemesReloaded();
    }
  }

  private void reloadKeepingActiveScheme() {
    String activeScheme = mySchemeManager.getCurrentSchemeName();
    mySchemeManager.reload();

    if (Strings.isNotEmpty(activeScheme)) {
      EditorColorsScheme scheme = getScheme(activeScheme);
      if (scheme != null) {
        setGlobalScheme(scheme);
      }
    }
  }

  private void initDefaultSchemes() {
    for (DefaultColorsScheme defaultScheme : DefaultColorSchemesManager.getInstance().getAllSchemes()) {
      mySchemeManager.addScheme(defaultScheme);
    }
  }

  private void initEditableDefaultSchemesCopies() {
    for (DefaultColorsScheme defaultScheme : DefaultColorSchemesManager.getInstance().getAllSchemes()) {
      if (defaultScheme.hasEditableCopy()) {
        createEditableCopy(defaultScheme, defaultScheme.getEditableCopyName());
      }
    }
  }

  // initScheme has to execute only after the LaF has been set in LafManagerImpl.initializeComponent
  private void initScheme(@NotNull UIManager.LookAndFeelInfo currentLaf) {
    EditorColorsScheme scheme = null;
    String wizardEditorScheme = WelcomeWizardUtil.getWizardEditorScheme();
    if (wizardEditorScheme != null) {
      scheme = getScheme(wizardEditorScheme);
      LOG.assertTrue(scheme != null, "Wizard scheme " + wizardEditorScheme + " not found");
    }

    if (!themeIsCustomized && scheme == null) {
      if (currentLaf instanceof UIThemeBasedLookAndFeelInfo) {
        String schemeName = ((UIThemeBasedLookAndFeelInfo)currentLaf).getTheme().getEditorSchemeName();
        if (schemeName != null) {
          scheme = getScheme(schemeName);
        }
      }
      else if (currentLaf.getName().contains("Darcula")) {
        scheme = getScheme("Darcula");
      }
    }

    if (scheme != null) {
      mySchemeManager.setCurrent(scheme, false);
    }
  }

  private void loadBundledSchemes() {
    if (!isUnitTestOrHeadlessMode()) {
      BUNDLED_EP_NAME.processWithPluginDescriptor((ep, pluginDescriptor) -> {
        mySchemeManager.loadBundledScheme(ep.getPath() + ".xml", null, pluginDescriptor);
      });
    }
  }

  private void loadSchemesFromThemes() {
    if (isUnitTestOrHeadlessMode()) {
      return;
    }

    for (UIThemeBasedLookAndFeelInfo laf : UiThemeProviderListManager.getInstance().getLaFs()) {
      UITheme theme = laf.getTheme();
      String path = theme.getEditorScheme();
      if (path != null) {
        mySchemeManager.loadBundledScheme(path, theme, null);
      }
    }
  }

  private void initEditableBundledSchemesCopies() {
    for (EditorColorsScheme scheme : mySchemeManager.getAllSchemes()) {
      if (scheme instanceof BundledScheme) {
        createEditableCopy((BundledScheme)scheme, Scheme.EDITABLE_COPY_PREFIX + scheme.getName());
      }
    }
  }

  private void resolveLinksToBundledSchemes() {
    List<EditorColorsScheme> brokenSchemesList = new ArrayList<>();
    for (EditorColorsScheme scheme : mySchemeManager.getAllSchemes()) {
      try {
        resolveSchemeParent(scheme);
      }
      catch (InvalidDataException e) {
        brokenSchemesList.add(scheme);
        String message = IdeBundle
          .message("notification.content.color.scheme", scheme.getName(),
                   e.getMessage());
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, IdeBundle.message("notification.title.incompatible.color.scheme"), message, NotificationType.ERROR));
      }
    }
    for (EditorColorsScheme brokenScheme : brokenSchemesList) {
      mySchemeManager.removeScheme(brokenScheme);
    }
  }

  @Override
  public void resolveSchemeParent(@NotNull EditorColorsScheme scheme) {
    if (scheme instanceof AbstractColorsScheme && !(scheme instanceof ReadOnlyColorsScheme)) {
      ((AbstractColorsScheme)scheme).resolveParent(name -> mySchemeManager.findSchemeByName(name));
    }
  }

  private void createEditableCopy(@NotNull AbstractColorsScheme initialScheme, @NotNull String editableCopyName) {
    AbstractColorsScheme editableCopy = (AbstractColorsScheme)getScheme(editableCopyName);
    if (editableCopy == null) {
      editableCopy = (AbstractColorsScheme)initialScheme.clone();
      editableCopy.setName(editableCopyName);
      mySchemeManager.addScheme(editableCopy);
    }
    editableCopy.setCanBeDeleted(false);
  }

  public void schemeChangedOrSwitched(@Nullable EditorColorsScheme newScheme) {
    // refreshAllEditors is not enough - for example, change "Errors and warnings -> Typo" from green (default) to red
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      PsiManager.getInstance(project).dropPsiCaches();
    }

    // we need to push events to components that use editor font, e.g. HTML editor panes
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).globalSchemeChange(newScheme);
    myTreeDispatcher.getMulticaster().globalSchemeChange(newScheme);
  }

  public void handleThemeAdded(@NotNull UITheme theme) {
    String editorScheme = theme.getEditorScheme();
    if (editorScheme != null) {
      getSchemeManager().loadBundledScheme(editorScheme, theme, null);
      initEditableBundledSchemesCopies();
    }
  }

  public void handleThemeRemoved(@NotNull UITheme theme) {
    String editorSchemeName = theme.getEditorSchemeName();
    if (editorSchemeName != null) {
      EditorColorsScheme scheme = mySchemeManager.findSchemeByName(editorSchemeName);
      if (scheme != null) {
        mySchemeManager.removeScheme(scheme);
        String editableCopyName = getEditableCopyName(scheme);
        if (editableCopyName != null) {
          mySchemeManager.removeScheme(editableCopyName);
        }
      }
    }
  }

  static final class BundledScheme extends EditorColorsSchemeImpl implements ReadOnlyColorsScheme {
    BundledScheme() {
      super(null);
    }

    @Override
    public boolean isVisible() {
      return false;
    }
  }

  static final class State {
    public boolean USE_ONLY_MONOSPACED_FONTS = true;

    @OptionTag(tag = "global_color_scheme", nameAttribute = "", valueAttribute = "name")
    public String colorScheme;
  }

  private static boolean isUnitTestOrHeadlessMode() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public TextAttributes getDefaultAttributes(@NotNull TextAttributesKey key) {
    final boolean dark = StartupUiUtil.isUnderDarcula() && getScheme("Darcula") != null;
    // It is reasonable to fetch attributes from Default color scheme. Otherwise, if we launch IDE and then
    // try switch from custom colors scheme (e.g. with dark background) to default one. Editor will show
    // incorrect highlighting with "traces" of color scheme which was active during IDE startup.
    return getScheme(dark ? "Darcula" : EditorColorsScheme.DEFAULT_SCHEME_NAME).getAttributes(key);
  }

  private static @NotNull Map<String, List<AdditionalTextAttributesEP>> collectAdditionalTextAttributesEPs() {
    Map<String, List<AdditionalTextAttributesEP>> result = new HashMap<>();
    ADDITIONAL_TEXT_ATTRIBUTES_EP_NAME.forEachExtensionSafe(attributesEP -> {
      result.computeIfAbsent(attributesEP.scheme, __ -> new ArrayList<>()).add(attributesEP);
    });
    return result;
  }

  private void loadRemainAdditionalTextAttributes(@NotNull Map<String, List<AdditionalTextAttributesEP>> additionalTextAttributes) {
    for (Map.Entry<String, List<AdditionalTextAttributesEP>> entry : additionalTextAttributes.entrySet()) {
      String schemeName = entry.getKey();
      EditorColorsScheme editorColorsScheme = mySchemeManager.findSchemeByName(schemeName);
      if (!(editorColorsScheme instanceof AbstractColorsScheme)) {
        if (!isUnitTestOrHeadlessMode()) {
          LOG.warn("Cannot find scheme: " + schemeName + " from plugins: " +
                   StringUtil.join(entry.getValue(), ep -> ep.pluginDescriptor.getPluginId().getIdString(), ";"));
        }
        continue;
      }

      loadAdditionalTextAttributesForScheme((AbstractColorsScheme)editorColorsScheme, entry.getValue());
    }
    additionalTextAttributes.clear();
  }

  private static void loadAdditionalTextAttributesForScheme(@NotNull AbstractColorsScheme scheme,
                                                            @NotNull Collection<AdditionalTextAttributesEP> attributeEps) {
    for (AdditionalTextAttributesEP attributesEP : attributeEps) {
      try {
        byte[] data =
          ResourceUtil.getResourceAsBytes(Strings.trimStart(attributesEP.file, "/"), attributesEP.pluginDescriptor.getClassLoader());
        if (data == null) {
          LOG.warn("resource not found: " + attributesEP.file);
          continue;
        }

        Element root = JDOMUtil.load(data);
        scheme.readAttributes(Objects.requireNonNullElse(root.getChild("attributes"), root));
        Element colors = root.getChild("colors");
        if (colors != null) {
          scheme.readColors(colors);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void addColorsScheme(@NotNull EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && !Strings.isEmpty(scheme.getName())) {
      mySchemeManager.addScheme(scheme);
    }
  }

  @Override
  public EditorColorsScheme @NotNull [] getAllSchemes() {
    EditorColorsScheme[] result = getAllVisibleSchemes(mySchemeManager.getAllSchemes());
    Arrays.sort(result, EditorColorSchemesComparator.INSTANCE);
    return result;
  }

  private static EditorColorsScheme[] getAllVisibleSchemes(@NotNull Collection<? extends EditorColorsScheme> schemes) {
    List<EditorColorsScheme> visibleSchemes = new ArrayList<>(schemes.size() - 1);
    for (EditorColorsScheme scheme : schemes) {
      if (AbstractColorsScheme.isVisible(scheme)) {
        visibleSchemes.add(scheme);
      }
    }
    return visibleSchemes.toArray(new EditorColorsScheme[0]);
  }

  @Override
  public void setGlobalScheme(@Nullable EditorColorsScheme scheme) {
    setGlobalScheme(scheme, false);
  }

  public void setGlobalScheme(@Nullable EditorColorsScheme scheme, boolean processChangeSynchronously) {
    boolean notify = LoadingState.COMPONENTS_LOADED.isOccurred();
    mySchemeManager.setCurrent(scheme == null ? getDefaultScheme() : scheme, notify, processChangeSynchronously);
  }

  private void setGlobalSchemeInner(@Nullable EditorColorsScheme scheme) {
    mySchemeManager.setCurrent(scheme == null ? getDefaultScheme() : scheme, false);
  }

  private @NotNull EditorColorsScheme getDefaultScheme() {
    DefaultColorsScheme defaultScheme = DefaultColorSchemesManager.getInstance().getFirstScheme();
    String editableCopyName = defaultScheme.getEditableCopyName();
    EditorColorsScheme editableCopy = getScheme(editableCopyName);
    assert editableCopy != null : "An editable copy of " + defaultScheme.getName() + " has not been initialized.";
    return editableCopy;
  }

  @Override
  public @NotNull EditorColorsScheme getGlobalScheme() {
    EditorColorsScheme scheme = mySchemeManager.getActiveScheme();
    EditorColorsScheme editableCopy = getEditableCopy(scheme);
    if (editableCopy != null) return editableCopy;
    return scheme == null ? getDefaultScheme() : scheme;
  }

  private @Nullable EditorColorsScheme getEditableCopy(EditorColorsScheme scheme) {
    if (isTempScheme(scheme)) return scheme;
    String editableCopyName = getEditableCopyName(scheme);
    if (editableCopyName != null) {
      EditorColorsScheme editableCopy = getScheme(editableCopyName);
      if (editableCopy != null) return editableCopy;
    }
    return null;
  }

  private static @Nullable String getEditableCopyName(EditorColorsScheme scheme) {
    String editableCopyName = null;
    if (scheme instanceof DefaultColorsScheme && ((DefaultColorsScheme)scheme).hasEditableCopy()) {
      editableCopyName = ((DefaultColorsScheme)scheme).getEditableCopyName();
    }
    else if (scheme instanceof BundledScheme) {
      editableCopyName = Scheme.EDITABLE_COPY_PREFIX + scheme.getName();
    }
    return editableCopyName;
  }

  @Override
  public EditorColorsScheme getScheme(@NonNls @NotNull String schemeName) {
    return mySchemeManager.findSchemeByName(schemeName);
  }

  @Override
  public void setUseOnlyMonospacedFonts(boolean value) {
    myState.USE_ONLY_MONOSPACED_FONTS = value;
  }

  @Override
  public boolean isUseOnlyMonospacedFonts() {
    return myState.USE_ONLY_MONOSPACED_FONTS;
  }

  @Override
  public @Nullable State getState() {
    String currentSchemeName = mySchemeManager.getCurrentSchemeName();
    if (currentSchemeName != null && !isTempScheme(mySchemeManager.getActiveScheme())) {
      myState.colorScheme = currentSchemeName;
    }
    return myState;
  }

  @Override
  public void noStateLoaded() {
    themeIsCustomized = false;
    setGlobalSchemeInner(StartupUiUtil.isUnderDarcula() ? getScheme("Darcula") : getDefaultScheme());
  }

  @Override
  public void loadState(@NotNull State state) {
    themeIsCustomized = true;
    myState = state;
    setGlobalSchemeInner(myState.colorScheme == null ? getDefaultScheme() : mySchemeManager.findSchemeByName(myState.colorScheme));
  }

  @Override
  public void initializeComponent() {
    Activity activity = StartUpMeasurer.startActivity("editor color scheme initialization");
    // LafManager is initialized in EDT, so, that's ok to call it here
    LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    // null in a headless mode
    if (laf != null) {
      initScheme(laf);
    }
    activity.end();
  }

  @Override
  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  @Override
  public @NotNull EditorColorsScheme getSchemeForCurrentUITheme() {
    LookAndFeelInfo lookAndFeelInfo = LafManager.getInstance().getCurrentLookAndFeel();
    EditorColorsScheme scheme = null;
    if (lookAndFeelInfo instanceof TempUIThemeBasedLookAndFeelInfo) {
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (isTempScheme(globalScheme)) {
        return globalScheme;
      }
    }
    if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
      UITheme theme = ((UIThemeBasedLookAndFeelInfo)lookAndFeelInfo).getTheme();
      String schemeName = theme.getEditorSchemeName();
      if (schemeName != null) {
        scheme = getScheme(schemeName);
        assert scheme != null : "Theme " + theme.getName() + " refers to unknown color scheme " + schemeName;
      }
    }
    if (scheme == null) {
      String schemeName = StartupUiUtil.isUnderDarcula() ? "Darcula" : DEFAULT_SCHEME_NAME;
      DefaultColorSchemesManager colorSchemeManager = DefaultColorSchemesManager.getInstance();
      scheme = colorSchemeManager.getScheme(schemeName);
      assert scheme != null :
        "The default scheme '" + schemeName + "' not found, " +
        "available schemes: " + colorSchemeManager.listNames();
    }
    EditorColorsScheme editableCopy = getEditableCopy(scheme);
    return editableCopy != null ? editableCopy : scheme;
  }

  public @NotNull SchemeManager<EditorColorsScheme> getSchemeManager() {
    return mySchemeManager;
  }

  private static final @NonNls String TEMP_SCHEME_KEY = "TEMP_SCHEME_KEY";
  private static final @NonNls String TEMP_SCHEME_FILE_KEY = "TEMP_SCHEME_FILE_KEY";
  public static boolean isTempScheme(EditorColorsScheme scheme) {
    if (scheme == null) return false;

    return StringUtil.equals(scheme.getMetaProperties().getProperty(TEMP_SCHEME_KEY), Boolean.TRUE.toString());
  }

  public static @Nullable Path getTempSchemeOriginalFilePath(EditorColorsScheme scheme) {
    if (isTempScheme(scheme)) {
      String path = scheme.getMetaProperties().getProperty(TEMP_SCHEME_FILE_KEY);
      if (path != null) {
        return Paths.get(path);
      }
    }
    return null;
  }

  public static void setTempScheme(EditorColorsScheme scheme, @Nullable VirtualFile originalSchemeFile) {
    if (scheme == null) return;
    scheme.getMetaProperties().setProperty(TEMP_SCHEME_KEY, Boolean.TRUE.toString());
    if (originalSchemeFile != null) {
      scheme.getMetaProperties().setProperty(TEMP_SCHEME_FILE_KEY, originalSchemeFile.getPath());
    }
  }
}
