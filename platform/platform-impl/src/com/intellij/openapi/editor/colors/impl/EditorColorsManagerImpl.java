/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@State(
  name = "EditorColorsManagerImpl",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/colors.scheme.xml"),
  additionalExportFile = EditorColorsManagerImpl.FILE_SPEC
)
public class EditorColorsManagerImpl extends EditorColorsManager implements PersistentStateComponent<EditorColorsManagerImpl.State> {
  private static final Logger LOG = Logger.getInstance(EditorColorsManagerImpl.class);

  @NonNls private static final String SCHEME_NODE_NAME = "scheme";
  private static final String DEFAULT_NAME = "Default";

  private final EventDispatcher<EditorColorsListener> myListeners = EventDispatcher.create(EditorColorsListener.class);

  private final DefaultColorSchemesManager myDefaultColorSchemesManager;
  private final SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> mySchemesManager;
  static final String FILE_SPEC = StoragePathMacros.ROOT_CONFIG + "/colors";

  private State myState = new State();

  public EditorColorsManagerImpl(DefaultColorSchemesManager defaultColorSchemesManager, SchemesManagerFactory schemesManagerFactory) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;

    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<EditorColorsSchemeImpl>() {
      @NotNull
      @Override
      public EditorColorsSchemeImpl readScheme(@NotNull Element element) {
        EditorColorsSchemeImpl scheme = new EditorColorsSchemeImpl(null);
        scheme.readExternal(element);
        return scheme;
      }

      @Override
      public Element writeScheme(@NotNull final EditorColorsSchemeImpl scheme) {
        Element root = new Element(SCHEME_NODE_NAME);
        try {
          scheme.writeExternal(root);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
          return null;
        }
        return root;
      }

      @NotNull
      @Override
      public State getState(@NotNull EditorColorsSchemeImpl scheme) {
        return scheme instanceof ReadOnlyColorsScheme ? State.NON_PERSISTENT : State.POSSIBLY_CHANGED;
      }

      @Override
      public void onCurrentSchemeChanged(final Scheme newCurrentScheme) {
        fireChanges(mySchemesManager.getCurrentScheme());
      }

      @NotNull
      @NonNls
      @Override
      public String getSchemeExtension() {
        return ".icls";
      }

      @Override
      public boolean isUpgradeNeeded() {
        return true;
      }
    }, RoamingType.PER_USER);

    addDefaultSchemes();

    // Load default schemes from providers
    if (!isUnitTestOrHeadlessMode()) {
      for (BundledColorSchemeEP ep : BundledColorSchemeEP.EP_NAME.getExtensions()) {
        mySchemesManager.loadBundledScheme(ep.path + ".xml", ep, new ThrowableConvertor<Element, EditorColorsScheme, Throwable>() {
          @Override
          public EditorColorsScheme convert(Element element) throws Throwable {
            return new ReadOnlyColorsSchemeImpl(element);
          }
        });
      }
    }

    mySchemesManager.loadSchemes();

    loadAdditionalTextAttributes();

    String wizardEditorScheme = WelcomeWizardUtil.getWizardEditorScheme();
    EditorColorsScheme scheme = null;
    if (wizardEditorScheme != null) {
      scheme = getScheme(wizardEditorScheme);
      LOG.assertTrue(scheme != null, "Wizard scheme " + wizardEditorScheme + " not found");
    }
    setGlobalSchemeInner(scheme == null ? getDefaultScheme() : scheme);
  }

  static class ReadOnlyColorsSchemeImpl extends EditorColorsSchemeImpl implements ReadOnlyColorsScheme {
    public ReadOnlyColorsSchemeImpl(@NotNull Element element) {
      super(null);

      readExternal(element);
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
      EditorColorsScheme editorColorsScheme = mySchemesManager.findSchemeByName(attributesEP.scheme);
      if (editorColorsScheme == null) {
        if (!isUnitTestOrHeadlessMode()) {
          LOG.warn("Cannot find scheme: " + attributesEP.scheme + " from plugin: " + attributesEP.getPluginDescriptor().getPluginId());
        }
        continue;
      }
      try {
        URL resource = attributesEP.getLoaderForClass().getResource(attributesEP.file);
        assert resource != null;
        ((AbstractColorsScheme)editorColorsScheme).readAttributes(JDOMUtil.load(URLUtil.openStream(resource)));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void addColorsScheme(@NotNull EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && !StringUtil.isEmpty(scheme.getName())) {
      mySchemesManager.addScheme(scheme);
    }
  }

  @Override
  public void removeAllSchemes() {
  }

  @Override
  public void setSchemes(@NotNull List<EditorColorsScheme> schemes) {
    mySchemesManager.setSchemes(schemes);
  }

  private void addDefaultSchemes() {
    for (DefaultColorsScheme defaultScheme : myDefaultColorSchemesManager.getAllSchemes()) {
      mySchemesManager.addScheme(defaultScheme);
    }
  }

  @NotNull
  @Override
  public EditorColorsScheme[] getAllSchemes() {
    List<EditorColorsScheme> schemes = mySchemesManager.getAllSchemes();
    EditorColorsScheme[] result = schemes.toArray(new EditorColorsScheme[schemes.size()]);
    Arrays.sort(result, new Comparator<EditorColorsScheme>() {
      @Override
      public int compare(@NotNull EditorColorsScheme s1, @NotNull EditorColorsScheme s2) {
        if (isDefaultScheme(s1) && !isDefaultScheme(s2)) return -1;
        if (!isDefaultScheme(s1) && isDefaultScheme(s2)) return 1;
        if (s1.getName().equals(DEFAULT_NAME)) return -1;
        if (s2.getName().equals(DEFAULT_NAME)) return 1;
        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });
    return result;
  }

  @Override
  public void setGlobalScheme(@Nullable EditorColorsScheme scheme) {
    setGlobalSchemeInner(scheme);

    LafManager.getInstance().updateUI();
    EditorFactory.getInstance().refreshAllEditors();

    fireChanges(scheme);
  }

  private void setGlobalSchemeInner(@Nullable EditorColorsScheme scheme) {
    mySchemesManager.setCurrentSchemeName(scheme == null ? getDefaultScheme().getName() : scheme.getName());
  }

  @NotNull
  private DefaultColorsScheme getDefaultScheme() {
    return myDefaultColorSchemesManager.getAllSchemes()[0];
  }

  @NotNull
  @Override
  public EditorColorsScheme getGlobalScheme() {
    EditorColorsScheme scheme = mySchemesManager.getCurrentScheme();
    return scheme == null ? getDefaultScheme() : scheme;
  }

  @Override
  public EditorColorsScheme getScheme(@NotNull String schemeName) {
    return mySchemesManager.findSchemeByName(schemeName);
  }

  private void fireChanges(EditorColorsScheme scheme) {
    myListeners.getMulticaster().globalSchemeChange(scheme);
  }

  @Override
  public void addEditorColorsListener(@NotNull EditorColorsListener listener) {
    myListeners.addListener(listener);
  }

  @Override
  public void addEditorColorsListener(@NotNull EditorColorsListener listener, @NotNull Disposable disposable) {
    myListeners.addListener(listener, disposable);
  }

  @Override
  public void removeEditorColorsListener(@NotNull EditorColorsListener listener) {
    myListeners.removeListener(listener);
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
    if (mySchemesManager.getCurrentScheme() != null) {
      String name = mySchemesManager.getCurrentScheme().getName();
      myState.colorScheme = "Default".equals(name) ? null : name;
    }
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
    setGlobalSchemeInner(myState.colorScheme == null ? getDefaultScheme() : mySchemesManager.findSchemeByName(myState.colorScheme));
  }

  @Override
  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  @TestOnly
  public SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> getSchemesManager() {
    return mySchemesManager;
  }
}
