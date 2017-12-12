// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.configurationStore.BundledSchemeEP;
import com.intellij.configurationStore.LazySchemeProcessor;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.configurationStore.SchemeExtensionProvider;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.PersistentStateComponent;
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
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ComponentTreeEventDispatcher;
import com.intellij.util.JdomKt;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@State(
  name = "EditorColorsManagerImpl",
  storages = @Storage("colors.scheme.xml"),
  additionalExportFile = EditorColorsManagerImpl.FILE_SPEC
)
public class EditorColorsManagerImpl extends EditorColorsManager implements PersistentStateComponent<EditorColorsManagerImpl.State> {
  private static final Logger LOG = Logger.getInstance(EditorColorsManagerImpl.class);
  private static final ExtensionPointName<BundledSchemeEP> BUNDLED_EP_NAME = ExtensionPointName.create("com.intellij.bundledColorScheme");

  private final ComponentTreeEventDispatcher<EditorColorsListener> myTreeDispatcher = ComponentTreeEventDispatcher.create(EditorColorsListener.class);

  private final DefaultColorSchemesManager myDefaultColorSchemeManager;
  private final SchemeManager<EditorColorsScheme> mySchemeManager;
  static final String FILE_SPEC = "colors";

  private State myState = new State();

  public EditorColorsManagerImpl(@NotNull DefaultColorSchemesManager defaultColorSchemeManager, @NotNull SchemeManagerFactory schemeManagerFactory) {
    myDefaultColorSchemeManager = defaultColorSchemeManager;

    class EditorColorSchemeProcessor extends LazySchemeProcessor<EditorColorsScheme, EditorColorsSchemeImpl> implements SchemeExtensionProvider {
      @NotNull
      @Override
      public EditorColorsSchemeImpl createScheme(@NotNull SchemeDataHolder<? super EditorColorsSchemeImpl> dataHolder,
                                                 @NotNull String name,
                                                 @NotNull Function<String, String> attributeProvider,
                                                 boolean isBundled) {
        EditorColorsSchemeImpl scheme = isBundled ? new BundledScheme() : new EditorColorsSchemeImpl(null);
        // todo be lazy
        scheme.readExternal(dataHolder.read());
        // we don't need to update digest for bundled scheme because
        // 1) it can be computed on demand later (because bundled scheme is not mutable)
        // 2) in the future user copy of bundled scheme will use bundled scheme as parent (not as full copy)
        if (isBundled) {
          scheme.optimizeAttributeMap();
        }
        return scheme;
      }

      @NotNull
      @Override
      public SchemeState getState(@NotNull EditorColorsScheme scheme) {
        return scheme instanceof ReadOnlyColorsScheme ? SchemeState.NON_PERSISTENT : SchemeState.POSSIBLY_CHANGED;
      }

      @Override
      public void onCurrentSchemeSwitched(@Nullable EditorColorsScheme oldScheme, @Nullable EditorColorsScheme newScheme) {
        ApplicationManager.getApplication().invokeLater(() -> { // don't do heavy operations right away
          LafManager.getInstance().updateUI();
          schemeChangedOrSwitched(newScheme);
        });
      }

      @NotNull
      @NonNls
      @Override
      public String getSchemeExtension() {
        return COLOR_SCHEME_FILE_EXTENSION;
      }

      @Override
      public boolean isSchemeEqualToBundled(@NotNull EditorColorsSchemeImpl scheme) {
        if (!scheme.getName().startsWith(SchemeManager.EDITABLE_COPY_PREFIX)) {
          return false;
        }

        AbstractColorsScheme bundledScheme =
          (AbstractColorsScheme)mySchemeManager.findSchemeByName(scheme.getName().substring(SchemeManager.EDITABLE_COPY_PREFIX.length()));
        if (bundledScheme == null) {
          return false;
        }

        return scheme.settingsEqual(bundledScheme);
      }

      @Override
      public void reloaded(@NotNull SchemeManager<EditorColorsScheme> schemeManager,
                           @NotNull Collection<? extends EditorColorsScheme> schemes) {
        loadBundledSchemes();
        initEditableDefaultSchemesCopies();
        initEditableBundledSchemesCopies();
      }
    }
    mySchemeManager = schemeManagerFactory.create(FILE_SPEC, new EditorColorSchemeProcessor());

    initDefaultSchemes();
    loadBundledSchemes();
    mySchemeManager.loadSchemes();

    String wizardEditorScheme = WelcomeWizardUtil.getWizardEditorScheme();
    EditorColorsScheme scheme = null;
    if (wizardEditorScheme != null) {
      scheme = getScheme(wizardEditorScheme);
      LOG.assertTrue(scheme != null, "Wizard scheme " + wizardEditorScheme + " not found");
    }
    
    initEditableDefaultSchemesCopies();
    initEditableBundledSchemesCopies();
    resolveLinksToBundledSchemes();
    setGlobalSchemeInner(scheme == null ? getDefaultScheme() : scheme);
  }

  private void initDefaultSchemes() {
    for (DefaultColorsScheme defaultScheme : myDefaultColorSchemeManager.getAllSchemes()) {
      mySchemeManager.addScheme(defaultScheme);
    }
    loadAdditionalTextAttributes();
  }

  private void initEditableDefaultSchemesCopies() {
    for (DefaultColorsScheme defaultScheme : myDefaultColorSchemeManager.getAllSchemes()) {
      if (defaultScheme.hasEditableCopy()) {
        createEditableCopy(defaultScheme, defaultScheme.getEditableCopyName());
      }
    }
  }

  private void loadBundledSchemes() {
    if (!isUnitTestOrHeadlessMode()) {
      for (BundledSchemeEP ep : BUNDLED_EP_NAME.getExtensions()) {
        mySchemeManager.loadBundledScheme(ep.getPath() + ".xml", ep);
      }
    }
  }
  
  private void initEditableBundledSchemesCopies() {
    for (EditorColorsScheme scheme : mySchemeManager.getAllSchemes()) {
      if (scheme instanceof BundledScheme) {
        createEditableCopy((BundledScheme)scheme, SchemeManager.EDITABLE_COPY_PREFIX + scheme.getName());
      }
    }
  }
  
  private void resolveLinksToBundledSchemes() {
    for (EditorColorsScheme scheme : mySchemeManager.getAllSchemes()) {
      if (scheme instanceof AbstractColorsScheme && !(scheme instanceof ReadOnlyColorsScheme)) {
        try {
          ((AbstractColorsScheme)scheme).resolveParent(name -> mySchemeManager.findSchemeByName(name));
        }
        catch (InvalidDataException e) {
          String message = "Color scheme '" + scheme.getName() + "'" +
                           " points to incorrect or non-existent default (base) scheme " +
                           e.getMessage();
          Notifications.Bus.notify(
            new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Incompatible color scheme", message, NotificationType.ERROR));
        }
      }
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

  @Deprecated
  public static void schemeChangedOrSwitched() {
    EditorColorsManagerImpl manager = (EditorColorsManagerImpl)getInstance();
    manager.schemeChangedOrSwitched(manager.getGlobalScheme());
  }

  public void schemeChangedOrSwitched(@Nullable EditorColorsScheme newScheme) {
    // refreshAllEditors is not enough - for example, change "Errors and warnings -> Typo" from green (default) to red
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }

    // we need to push events to components that use editor font, e.g. HTML editor panes
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).globalSchemeChange(newScheme);
    myTreeDispatcher.getMulticaster().globalSchemeChange(newScheme);
  }

  static class BundledScheme extends EditorColorsSchemeImpl implements ReadOnlyColorsScheme {
    public BundledScheme() {
      super(null);
    }

    @Override
    public boolean isVisible() {
      return false;
    }
  }

  static class State {
    public boolean USE_ONLY_MONOSPACED_FONTS = true;

    @OptionTag(tag = "global_color_scheme", nameAttribute = "", valueAttribute = "name")
    public String colorScheme;
  }

  private static boolean isUnitTestOrHeadlessMode() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public TextAttributes getDefaultAttributes(TextAttributesKey key) {
    final boolean dark = UIUtil.isUnderDarcula() && getScheme("Darcula") != null;
    // It is reasonable to fetch attributes from Default color scheme. Otherwise if we launch IDE and then
    // try switch from custom colors scheme (e.g. with dark background) to default one. Editor will show
    // incorrect highlighting with "traces" of color scheme which was active during IDE startup.
    return getScheme(dark ? "Darcula" : EditorColorsScheme.DEFAULT_SCHEME_NAME).getAttributes(key);
  }

  private void loadAdditionalTextAttributes() {
    for (AdditionalTextAttributesEP attributesEP : AdditionalTextAttributesEP.EP_NAME.getExtensions()) {
      EditorColorsScheme editorColorsScheme = mySchemeManager.findSchemeByName(attributesEP.scheme);
      if (editorColorsScheme == null) {
        if (!isUnitTestOrHeadlessMode()) {
          LOG.warn("Cannot find scheme: " + attributesEP.scheme + " from plugin: " + attributesEP.getPluginDescriptor().getPluginId());
        }
        continue;
      }
      URL resource = attributesEP.getLoaderForClass().getResource(attributesEP.file);
      if (resource == null) {
        LOG.warn("resource not found: " + attributesEP.file);
        continue;
      }
      try {
        ((AbstractColorsScheme)editorColorsScheme).readAttributes(JdomKt.loadElement(URLUtil.openStream(resource)));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void addColorsScheme(@NotNull EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && !StringUtil.isEmpty(scheme.getName())) {
      mySchemeManager.addScheme(scheme);
    }
  }

  @Override
  public void removeAllSchemes() {
  }

  @Override
  public void setSchemes(@NotNull List<EditorColorsScheme> schemes) {
    mySchemeManager.setSchemes(schemes);
  }

  @NotNull
  @Override
  public EditorColorsScheme[] getAllSchemes() {
    EditorColorsScheme[] result = getAllVisibleSchemes(mySchemeManager.getAllSchemes());
    Arrays.sort(result, EditorColorSchemesComparator.INSTANCE);
    return result;
  }

  private static EditorColorsScheme[] getAllVisibleSchemes(@NotNull Collection<EditorColorsScheme> schemes) {
    List<EditorColorsScheme> visibleSchemes = new ArrayList<>(schemes.size() - 1);
    for (EditorColorsScheme scheme : schemes) {
      if (AbstractColorsScheme.isVisible(scheme)) {
        visibleSchemes.add(scheme);
      }
    }
    return visibleSchemes.toArray(new EditorColorsScheme[visibleSchemes.size()]);
  }

  @Override
  public void setGlobalScheme(@Nullable EditorColorsScheme scheme) {
    Application application = ApplicationManager.getApplication();
    boolean notify = (application instanceof ApplicationImpl && ((ApplicationImpl)application).isLoaded());
    mySchemeManager.setCurrent(scheme == null ? getDefaultScheme() : scheme, notify);
  }

  private void setGlobalSchemeInner(@Nullable EditorColorsScheme scheme) {
    mySchemeManager.setCurrent(scheme == null ? getDefaultScheme() : scheme, false);
  }

  @NotNull
  private EditorColorsScheme getDefaultScheme() {
    DefaultColorsScheme defaultScheme = myDefaultColorSchemeManager.getFirstScheme();
    String editableCopyName = defaultScheme.getEditableCopyName();
    EditorColorsScheme editableCopy = getScheme(editableCopyName);
    assert editableCopy != null : "An editable copy of " + defaultScheme.getName() + " has not been initialized.";
    return editableCopy;
  }

  @NotNull
  @Override
  public EditorColorsScheme getGlobalScheme() {
    EditorColorsScheme scheme = mySchemeManager.getActiveScheme();
    String editableCopyName = null;
    if (scheme instanceof DefaultColorsScheme && ((DefaultColorsScheme)scheme).hasEditableCopy()) {
      editableCopyName = ((DefaultColorsScheme)scheme).getEditableCopyName();
    }
    else if (scheme instanceof BundledScheme) {
      editableCopyName = SchemeManager.EDITABLE_COPY_PREFIX + scheme.getName();
    }
    if (editableCopyName != null) {
      EditorColorsScheme editableCopy = getScheme(editableCopyName);
      if (editableCopy != null) return editableCopy;
    }
    return scheme == null ? getDefaultScheme() : scheme;
  }

  @Override
  public EditorColorsScheme getScheme(@NotNull String schemeName) {
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

  @Nullable
  @Override
  public State getState() {
    String currentSchemeName = mySchemeManager.getCurrentSchemeName();
    if (currentSchemeName != null) {
      myState.colorScheme = ("Default".equals(currentSchemeName) || (SchemeManager.EDITABLE_COPY_PREFIX + "Default").equals(
        currentSchemeName)) ? null : currentSchemeName;
    }
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
    setGlobalSchemeInner(myState.colorScheme == null ? getDefaultScheme() : mySchemeManager.findSchemeByName(myState.colorScheme));
  }

  @Override
  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  @NotNull
  @Override
  public EditorColorsScheme getSchemeForCurrentUITheme() {
    String schemeName = UIUtil.isUnderDarcula() ? "Darcula" : DEFAULT_SCHEME_NAME;
    EditorColorsScheme scheme = myDefaultColorSchemeManager.getScheme(schemeName);
    assert scheme != null :
      "The default scheme '" + schemeName + "' not found, " +
      "available schemes: " + Arrays.toString(myDefaultColorSchemeManager.listNames());
    if (((DefaultColorsScheme)scheme).hasEditableCopy()) {
      EditorColorsScheme editableCopy = getScheme(((DefaultColorsScheme)scheme).getEditableCopyName());
      if (editableCopy != null) return editableCopy;
    }
    return scheme;
  }

  @NotNull
  public SchemeManager<EditorColorsScheme> getSchemeManager() {
    return mySchemeManager;
  }
}
