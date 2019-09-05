/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.messages.MessageBusConnection;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author traff
 */
public class JBTerminalSystemSettingsProviderBase extends DefaultTabbedSettingsProvider implements Disposable {
  private final MyColorsSchemeDelegate myColorsScheme;
  private JBTerminalSchemeColorPalette myColorPalette;

  public JBTerminalSystemSettingsProviderBase() {
    myColorsScheme = createBoundColorSchemeDelegate();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
      int oldSize = myColorsScheme.getConsoleFontSize();
      int newSize = myColorsScheme.detectConsoleFontSize();
      if (oldSize != newSize) {
        myColorsScheme.setConsoleFontSize(newSize);
        fireFontChanged();
      }
    });
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        myColorsScheme.updateGlobalScheme(scheme);
        myColorsScheme.setConsoleFontSize(myColorsScheme.detectConsoleFontSize());
        myColorPalette = null;
        fireFontChanged();
      }
    });
  }

  private final Set<TerminalSettingsListener> myListeners = new HashSet<>();

  @Override
  public KeyStroke[] getCopyKeyStrokes() {
    return getKeyStrokesByActionId("$Copy");
  }

  @Override
  public KeyStroke[] getPasteKeyStrokes() {
    return getKeyStrokesByActionId("$Paste");
  }

  @Override
  public KeyStroke[] getNextTabKeyStrokes() {
    return getKeyStrokesByActionId("NextTab");
  }

  @Override
  public KeyStroke[] getPreviousTabKeyStrokes() {
    return getKeyStrokesByActionId("PreviousTab");
  }

  @Override
  public KeyStroke[] getFindKeyStrokes() {
    return getKeyStrokesByActionId("Find");
  }

  @NotNull
  @Override
  public ColorPalette getTerminalColorPalette() {
    JBTerminalSchemeColorPalette colorPalette = myColorPalette;
    if (colorPalette == null) {
      colorPalette = new JBTerminalSchemeColorPalette(myColorsScheme);
      myColorPalette = colorPalette;
    }
    return colorPalette;
  }

  private KeyStroke[] getKeyStrokesByActionId(String actionId) {
    java.util.List<KeyStroke> keyStrokes = new ArrayList<>();
    Shortcut[] shortcuts = getActiveKeymapShortcuts(actionId).getShortcuts();
    for (Shortcut sc : shortcuts) {
      if (sc instanceof KeyboardShortcut) {
        KeyStroke ks = ((KeyboardShortcut)sc).getFirstKeyStroke();
        keyStrokes.add(ks);
      }
    }

    return keyStrokes.toArray(new KeyStroke[0]);
  }

  @Override
  public void dispose() {

  }

  public void addListener(TerminalSettingsListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(TerminalSettingsListener listener) {
    myListeners.remove(listener);
  }

  public void fireFontChanged() {
    for (TerminalSettingsListener l : myListeners) {
      l.fontChanged();
    }
  }

  @NotNull
  public KeyStroke[] getShowTabsKeyStrokes() {
    return getKeyStrokesByActionId(ShowContentAction.ACTION_ID);
  }

  public KeyStroke[] getMoveTabRightKeyStrokes() {
    return getKeyStrokesByActionId("Terminal.MoveToolWindowTabRight");
  }

  public KeyStroke[] getMoveTabLeftKeyStrokes() {
    return getKeyStrokesByActionId("Terminal.MoveToolWindowTabLeft");
  }

  static class MyColorsSchemeDelegate implements EditorColorsScheme {

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
    public FontSize getQuickDocFontSize() {
      return myGlobalScheme.getQuickDocFontSize();
    }

    @Override
    public void setQuickDocFontSize(@NotNull FontSize fontSize) {
      myGlobalScheme.setQuickDocFontSize(fontSize);
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
    @Nullable
    public Object clone() {
      return null;
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

  @NotNull
  private static MyColorsSchemeDelegate createBoundColorSchemeDelegate() {
    return new MyColorsSchemeDelegate();
  }

  @NotNull
  MyColorsSchemeDelegate getColorsScheme() {
    return myColorsScheme;
  }

  @Override
  public float getLineSpace() {
    return myColorsScheme.getConsoleLineSpacing();
  }

  @Override
  public TextStyle getSelectionColor() {
    return new TextStyle(TerminalColor.awt(myColorsScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR)),
                         TerminalColor.awt(myColorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)));
  }

  @Override
  public TextStyle getFoundPatternColor() {
    return new TextStyle(TerminalColor.awt(myColorsScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getForegroundColor()),
                         TerminalColor.awt(myColorsScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getBackgroundColor()));
  }

  @Override
  public TextStyle getHyperlinkColor() {
    return new TextStyle(TerminalColor.awt(myColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getForegroundColor()),
                         TerminalColor.awt(myColorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getBackgroundColor()));
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(TerminalColor.index(JBTerminalSchemeColorPalette.getDefaultForegroundIndex()),
                         TerminalColor.index(JBTerminalSchemeColorPalette.getDefaultBackgroundIndex()));
  }

  @Override
  public Font getTerminalFont() {
    return new Font(getFontName(), Font.PLAIN, (int)getTerminalFontSize());
  }

  public String getFontName() {
    List<String> fonts = myColorsScheme.getConsoleFontPreferences().getEffectiveFontFamilies();

    if (fonts.size() > 0) {
      return fonts.get(0);
    }

    return "Monospaced-14";
  }

  @Override
  public float getTerminalFontSize() {
    return (float)myColorsScheme.getConsoleFontSize();
  }

  @Override
  public boolean useAntialiasing() {
    return true; // we return true here because all the settings are checked again in UiSettings.setupAntialiasing
  }

  @Override
  public int caretBlinkingMs() {
    if (!EditorSettingsExternalizable.getInstance().isBlinkCaret()) {
      return 0;
    }
    return EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }

  @Override
  public int getBufferMaxLinesCount() {
    final int linesCount = Registry.get("terminal.buffer.max.lines.count").asInteger();
    if (linesCount > 0) {
      return linesCount;
    }
    else {
      return super.getBufferMaxLinesCount();
    }
  }

  public boolean overrideIdeShortcuts() {
    return false;
  }

  @Override
  public boolean useInverseSelectionColor() {
    return false;
  }
}
