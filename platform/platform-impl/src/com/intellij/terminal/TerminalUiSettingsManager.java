// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import com.jediterm.terminal.emulator.ColorPalette;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TerminalUiSettingsManager implements Disposable {

  private final MyColorsSchemeDelegate myColorsScheme;
  private JBTerminalSchemeColorPalette myColorPalette;
  private final List<JBTerminalPanel> myTerminalPanels = new CopyOnWriteArrayList<>();

  TerminalUiSettingsManager() {
    myColorsScheme = new MyColorsSchemeDelegate();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
      int oldSize = myColorsScheme.getConsoleFontSize();
      int newSize = myColorsScheme.detectConsoleFontSize();
      if (oldSize != newSize) {
        setConsoleFontSize(newSize);
      }
    });
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        myColorsScheme.updateGlobalScheme(scheme);
        myColorPalette = null;
        setConsoleFontSize(myColorsScheme.detectConsoleFontSize());
      }
    });
  }

  void setConsoleFontSize(int fontSize) {
    myColorsScheme.setConsoleFontSize(fontSize);
    fireFontChanged();
  }

  static @NotNull TerminalUiSettingsManager getInstance() {
    return ServiceManager.getService(TerminalUiSettingsManager.class);
  }

  void addListener(@NotNull JBTerminalPanel terminalPanel) {
    myTerminalPanels.add(terminalPanel);
    Disposer.register(terminalPanel, () -> myTerminalPanels.remove(terminalPanel));
  }

  private void fireFontChanged() {
    for (JBTerminalPanel panel : myTerminalPanels) {
      panel.fontChanged();
    }
  }

  @NotNull EditorColorsScheme getEditorColorsScheme() {
    return myColorsScheme;
  }

  @NotNull ColorPalette getTerminalColorPalette() {
    JBTerminalSchemeColorPalette colorPalette = myColorPalette;
    if (colorPalette == null) {
      colorPalette = new JBTerminalSchemeColorPalette(myColorsScheme);
      myColorPalette = colorPalette;
    }
    return colorPalette;
  }

  @Override
  public void dispose() {}

  private static final class MyColorsSchemeDelegate implements EditorColorsScheme {

    private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();
    private final HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<>();
    private final HashMap<ColorKey, Color> myOwnColors = new HashMap<>();
    private Map<EditorFontType, Font> myFontsMap = null;
    private String myFaceName = null;
    private EditorColorsScheme myGlobalScheme;

    private int myConsoleFontSize;

    private MyColorsSchemeDelegate() {
      updateGlobalScheme(null);
      myConsoleFontSize = detectConsoleFontSize();
      initFonts();
    }

    @NotNull
    private EditorColorsScheme getGlobal() {
      return myGlobalScheme;
    }

    @NotNull
    @Override
    public String getName() {
      return getGlobal().getName();
    }

    private void initFonts() {
      String consoleFontName = getConsoleFontName();
      int consoleFontSize = getConsoleFontSize();
      myFontPreferences.clear();
      myFontPreferences.register(consoleFontName, consoleFontSize);

      myFontsMap = new EnumMap<>(EditorFontType.class);

      Font plainFont = new Font(consoleFontName, Font.PLAIN, consoleFontSize);
      Font boldFont = new Font(consoleFontName, Font.BOLD, consoleFontSize);
      Font italicFont = new Font(consoleFontName, Font.ITALIC, consoleFontSize);
      Font boldItalicFont = new Font(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize);

      myFontsMap.put(EditorFontType.PLAIN, plainFont);
      myFontsMap.put(EditorFontType.BOLD, boldFont);
      myFontsMap.put(EditorFontType.ITALIC, italicFont);
      myFontsMap.put(EditorFontType.BOLD_ITALIC, boldItalicFont);
    }

    @Override
    public void setName(String name) {
      getGlobal().setName(name);
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return getGlobal().getDisplayName();
    }

    @Override
    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getGlobal().getAttributes(key);
    }

    @Override
    public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    @NotNull
    @Override
    public Color getDefaultBackground() {
      Color color = getGlobal().getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
      return color != null ? color : getGlobal().getDefaultBackground();
    }

    @NotNull
    @Override
    public Color getDefaultForeground() {
      Color foregroundColor = getGlobal().getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).getForegroundColor();
      return foregroundColor != null ? foregroundColor : getGlobal().getDefaultForeground();
    }

    @Override
    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getGlobal().getColor(key);
    }

    @Override
    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);
    }

    @NotNull
    @Override
    public FontPreferences getFontPreferences() {
      return myGlobalScheme.getFontPreferences();
    }

    @Override
    public void setFontPreferences(@NotNull FontPreferences preferences) {
      throw new IllegalStateException();
    }

    @Override
    public int getEditorFontSize() {
      return getGlobal().getEditorFontSize();
    }

    @Override
    public void setEditorFontSize(int fontSize) {

    }

    @Override
    public String getEditorFontName() {
      return getGlobal().getEditorFontName();
    }

    @Override
    public void setEditorFontName(String fontName) {
      throw new IllegalStateException();
    }

    @NotNull
    @Override
    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getGlobal().getFont(key);
    }

    @Override
    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        initFonts();
      }
      myFontsMap.put(key, font);
    }

    @Override
    public float getLineSpacing() {
      return getGlobal().getLineSpacing();
    }

    @Override
    public void setLineSpacing(float lineSpacing) {
      getGlobal().setLineSpacing(lineSpacing);
    }

    @Override
    public Object clone() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void readExternal(Element element) {
    }

    private void updateGlobalScheme(@Nullable EditorColorsScheme scheme) {
      myFontsMap = null;
      myGlobalScheme = scheme != null ? scheme : EditorColorsManager.getInstance().getGlobalScheme();
    }

    @NotNull
    @Override
    public FontPreferences getConsoleFontPreferences() {
      return myFontPreferences;
    }

    @Override
    public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
      preferences.copyTo(myFontPreferences);
      initFonts();
    }

    @Override
    public String getConsoleFontName() {
      if (myFaceName == null) {
        return getGlobal().getConsoleFontName();
      }
      else {
        return myFaceName;
      }
    }

    @Override
    public void setConsoleFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
    }

    private int detectConsoleFontSize() {
      if (UISettings.getInstance().getPresentationMode()) {
        return UISettings.getInstance().getPresentationModeFontSize();
      }
      return getGlobal().getConsoleFontSize();
    }

    @Override
    public int getConsoleFontSize() {
      return myConsoleFontSize;
    }

    @Override
    public void setConsoleFontSize(int fontSize) {
      myConsoleFontSize = fontSize;
      initFonts();
    }

    @Override
    public float getConsoleLineSpacing() {
      return getGlobal().getConsoleLineSpacing();
    }

    @Override
    public void setConsoleLineSpacing(float lineSpacing) {
      getGlobal().setConsoleLineSpacing(lineSpacing);
    }

    @NotNull
    @Override
    public Properties getMetaProperties() {
      return myGlobalScheme.getMetaProperties();
    }
  }
}
