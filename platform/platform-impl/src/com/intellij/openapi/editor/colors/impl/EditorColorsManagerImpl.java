/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class EditorColorsManagerImpl extends EditorColorsManager implements NamedJDOMExternalizable, ExportableComponent, NamedComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl");

  private final EventDispatcher<EditorColorsListener> myListeners = EventDispatcher.create(EditorColorsListener.class);

  @NonNls private static final String NODE_NAME = "global_color_scheme";
  @NonNls private static final String SCHEME_NODE_NAME = "scheme";

  private String myGlobalSchemeName;
  public boolean USE_ONLY_MONOSPACED_FONTS = true;
  private final DefaultColorSchemesManager myDefaultColorSchemesManager;
  private final SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> mySchemesManager;
  @NonNls private static final String NAME_ATTR = "name";
  private static final String FILE_SPEC = "$ROOT_CONFIG$/colors";
  private static final String FILE_EXT = ".icls";

  public EditorColorsManagerImpl(DefaultColorSchemesManager defaultColorSchemesManager, SchemesManagerFactory schemesManagerFactory) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;

    mySchemesManager = schemesManagerFactory.createSchemesManager(
      FILE_SPEC,
      new MySchemeProcessor(), RoamingType.PER_USER);

    addDefaultSchemes();

    // Load default schemes from providers
    if (!isUnitTestOrHeadlessMode()) {
      loadSchemesFromBeans();
    }

    loadAllSchemes();

    loadAdditionalTextAttributes();

    setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
  }

  private static boolean isUnitTestOrHeadlessMode() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public TextAttributes getDefaultAttributes(TextAttributesKey key) {
    final boolean dark = UIUtil.isUnderDarcula() && getScheme("Darcula") != null;
    // It is reasonable to fetch attributes from Default color scheme. Otherwise if we launch IDE and then
    // try switch from custom colors scheme (e.g. with dark background) to default one. Editor will show
    // incorrect highlighting with "traces" of color scheme which was active during IDE startup.
    final EditorColorsScheme defaultColorScheme = getScheme(dark ? "Darcula" : EditorColorsScheme.DEFAULT_SCHEME_NAME);
    return defaultColorScheme.getAttributes(key);
  }

  private void loadSchemesFromBeans() {
    for (BundledColorSchemeEP schemeEP : Extensions.getExtensions(BundledColorSchemeEP.EP_NAME)) {
      String fileName = schemeEP.path + ".xml";
      InputStream stream = schemeEP.getLoaderForClass().getResourceAsStream(fileName);
      try {
        EditorColorsSchemeImpl scheme = loadSchemeFromStream(fileName, stream);
        if (scheme != null) {
          mySchemesManager.addNewScheme(scheme, false);
        }
      }
      catch (final Exception e) {
        LOG.error("Cannot read scheme from " + fileName + ": " + e.getLocalizedMessage(), e);
      }
    }
  }

  private void loadAdditionalTextAttributes() {
    for (AdditionalTextAttributesEP attributesEP : AdditionalTextAttributesEP.EP_NAME.getExtensions()) {
      final EditorColorsScheme editorColorsScheme = mySchemesManager.findSchemeByName(attributesEP.scheme);
      if (editorColorsScheme == null) {
        if (!isUnitTestOrHeadlessMode()) {
          LOG.warn("Cannot find scheme: " + attributesEP.scheme + " from plugin: " + attributesEP.getPluginDescriptor().getPluginId());
        }
        continue;
      }
      try {
        InputStream inputStream = attributesEP.getLoaderForClass().getResourceAsStream(attributesEP.file);
        Document document = JDOMUtil.loadDocument(inputStream);

        ((AbstractColorsScheme)editorColorsScheme).readAttributes(document.getRootElement());
      }
      catch (Exception e1) {
        LOG.error(e1);
      }
    }
  }

  private static EditorColorsSchemeImpl loadSchemeFromStream(String schemePath, InputStream inputStream)
    throws IOException, JDOMException, InvalidDataException {
    if (inputStream == null) {
      // Error shouldn't occur during this operation
      // thus we report error instead of info
      LOG.error("Cannot read scheme from " +  schemePath);
      return null;
    }

    final Document document;
    try {
      document = JDOMUtil.loadDocument(inputStream);
    }
    catch (JDOMException e) {
      LOG.info("Error reading scheme from  " + schemePath + ": " + e.getLocalizedMessage());
      throw e;
    }
    return loadSchemeFromDocument(document, false);
  }

  private static EditorColorsSchemeImpl loadSchemeFromDocument(final Document document,
                                                               final boolean isEditable)
    throws InvalidDataException {

    final Element root = document.getRootElement();

    if (root == null || !SCHEME_NODE_NAME.equals(root.getName())) {
      throw new InvalidDataException();
    }

    final EditorColorsSchemeImpl scheme = isEditable
       // editable scheme
       ? new EditorColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance())
       //not editable scheme
       : new ReadOnlyColorsSchemeImpl(null, DefaultColorSchemesManager.getInstance());
    scheme.readExternal(root);
    return scheme;
  }

  // -------------------------------------------------------------------------
  // Schemes manipulation routines
  // -------------------------------------------------------------------------

  @Override
  public void addColorsScheme(@NotNull EditorColorsScheme scheme) {
    if (!isDefaultScheme(scheme) && scheme.getName().trim().length() > 0) {
      mySchemesManager.addNewScheme(scheme, true);
    }
  }

  @Override
  public void removeAllSchemes() {
    mySchemesManager.clearAllSchemes();
    addDefaultSchemes();
  }

  private void addDefaultSchemes() {
    DefaultColorsScheme[] allDefaultSchemes = myDefaultColorSchemesManager.getAllSchemes();
    for (DefaultColorsScheme defaultScheme : allDefaultSchemes) {
      mySchemesManager.addNewScheme(defaultScheme, true);
    }
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  @NotNull
  @Override
  public EditorColorsScheme[] getAllSchemes() {
    ArrayList<EditorColorsScheme> schemes = new ArrayList<EditorColorsScheme>(mySchemesManager.getAllSchemes());
    Collections.sort(schemes, new Comparator<EditorColorsScheme>() {
      @Override
      public int compare(EditorColorsScheme s1, EditorColorsScheme s2) {
        if (isDefaultScheme(s1) && !isDefaultScheme(s2)) return -1;
        if (!isDefaultScheme(s1) && isDefaultScheme(s2)) return 1;

        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });

    return schemes.toArray(new EditorColorsScheme[schemes.size()]);
  }

  @Override
  public void setGlobalScheme(EditorColorsScheme scheme) {
    mySchemesManager.setCurrentSchemeName(scheme == null ? getDefaultScheme().getName() : scheme.getName());
    fireChanges(scheme);
  }

  @NotNull
  private static DefaultColorsScheme getDefaultScheme() {
    return DefaultColorSchemesManager.getInstance().getAllSchemes()[0];
  }

  @NotNull
  @Override
  public EditorColorsScheme getGlobalScheme() {
    final EditorColorsScheme scheme = mySchemesManager.getCurrentScheme();
    if (scheme == null) {
      return getDefaultScheme();
    }
    return scheme;
  }

  @Override
  public EditorColorsScheme getScheme(String schemeName) {
    return mySchemesManager.findSchemeByName(schemeName);
  }

  private void fireChanges(EditorColorsScheme scheme) {
    myListeners.getMulticaster().globalSchemeChange(scheme);
  }

  // -------------------------------------------------------------------------
  // Routines responsible for loading & saving colors schemes.
  // -------------------------------------------------------------------------

  private void loadAllSchemes() {
    mySchemesManager.loadSchemes();
  }

  private static File getColorsDir(boolean create) {
    @NonNls String directoryPath = PathManager.getConfigPath() + File.separator + "colors";
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!create) return null;
      if (!directory.mkdir()) {
        LOG.error("Cannot create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
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
  public void setUseOnlyMonospacedFonts(boolean b) {
    USE_ONLY_MONOSPACED_FONTS = b;
  }

  @Override
  public boolean isUseOnlyMonospacedFonts() {
    return USE_ONLY_MONOSPACED_FONTS;
  }

  @Override
  public String getExternalFileName() {
    return "colors.scheme";
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{getColorsDir(true), PathManager.getOptionsFile(this)};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return OptionsBundle.message("options.color.schemes.presentable.name");
  }

  @Override
  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    Element element = parentNode.getChild(NODE_NAME);
    if (element != null) {
      String name = element.getAttributeValue(NAME_ATTR);
      if (name != null && !"".equals(name.trim())) {
        myGlobalSchemeName = name;
      }
    }

    initGlobalScheme();
  }

  private void initGlobalScheme() {
    if (myGlobalSchemeName != null) {
      setGlobalSchemeByName(myGlobalSchemeName);
    }
    else {
      setGlobalScheme(myDefaultColorSchemesManager.getAllSchemes()[0]);
    }
  }

  private void setGlobalSchemeByName(String schemeName) {
    setGlobalScheme(mySchemesManager.findSchemeByName(schemeName));
  }

  @Override
  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    if (mySchemesManager.getCurrentScheme() != null) {
      Element element = new Element(NODE_NAME);
      element.setAttribute(NAME_ATTR, mySchemesManager.getCurrentScheme().getName());
      parentNode.addContent(element);
    }
  }

  @Override
  public boolean isDefaultScheme(EditorColorsScheme scheme) {
    return scheme instanceof DefaultColorsScheme;
  }

  public SchemesManager<EditorColorsScheme, EditorColorsSchemeImpl> getSchemesManager() {
    return mySchemesManager;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "EditorColorsManagerImpl";
  }

  private final class MySchemeProcessor extends BaseSchemeProcessor<EditorColorsSchemeImpl> implements SchemeExtensionProvider {
    @Override
    public EditorColorsSchemeImpl readScheme(final Document document)
      throws InvalidDataException {

      return loadSchemeFromDocument(document, true);
    }

    @Override
    public Document writeScheme(final EditorColorsSchemeImpl scheme) {
      Element root = new Element(SCHEME_NODE_NAME);
      try {
        scheme.writeExternal(root);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
        return null;
      }

      return new Document(root);
    }

    public void renameScheme(final String name, final EditorColorsScheme scheme) {
      scheme.setName(name);
    }

    @Override
    public boolean shouldBeSaved(final EditorColorsSchemeImpl scheme) {
      return !(scheme instanceof ReadOnlyColorsScheme);
    }

    @Override
    public void onCurrentSchemeChanged(final Scheme newCurrentScheme) {
      fireChanges(mySchemesManager.getCurrentScheme());
    }

    @NotNull
    @Override
    public String getSchemeExtension() {
      return FILE_EXT;
    }

    @Override
    public boolean isUpgradeNeeded() {
      return true;
    }
  }
}
