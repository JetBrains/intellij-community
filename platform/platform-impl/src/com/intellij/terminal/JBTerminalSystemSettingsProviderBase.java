// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

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
  public @NotNull TerminalActionPresentation getNewSessionActionPresentation() {
    TerminalActionPresentation presentation = super.getNewSessionActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.NewSession.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getOpenUrlActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.OpenAsUrl.text"), Collections.emptyList());
  }

  @Override
  public @NotNull TerminalActionPresentation getCopyActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.CopySelectedText");
    if (strokes.isEmpty()) {
      strokes = getKeyStrokesByActionId(IdeActions.ACTION_COPY);
    }
    return new TerminalActionPresentation(UIUtil.removeMnemonic(ActionsBundle.message("action.$Copy.text")), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getPasteActionPresentation() {
    List<KeyStroke> strokes = getKeyStrokesByActionId("Terminal.Paste");
    if (strokes.isEmpty()) {
      strokes = getKeyStrokesByActionId(IdeActions.ACTION_PASTE);
    }
    return new TerminalActionPresentation(UIUtil.removeMnemonic(ActionsBundle.message("action.$Paste.text")), strokes);
  }

  @Override
  public @NotNull TerminalActionPresentation getClearBufferActionPresentation() {
    TerminalActionPresentation presentation = super.getClearBufferActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.ClearBuffer.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getPageUpActionPresentation() {
    TerminalActionPresentation presentation = super.getPageUpActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.PageUp.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getPageDownActionPresentation() {
    TerminalActionPresentation presentation = super.getPageDownActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.PageDown.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getLineUpActionPresentation() {
    TerminalActionPresentation presentation = super.getLineUpActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.LineUp.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getLineDownActionPresentation() {
    TerminalActionPresentation presentation = super.getLineDownActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.LineDown.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getCloseSessionActionPresentation() {
    TerminalActionPresentation presentation = super.getCloseSessionActionPresentation();
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.CloseSession.text"),
                                          presentation.getKeyStrokes());
  }

  @Override
  public @NotNull TerminalActionPresentation getFindActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.Find.text"),
                                          getKeyStrokesByActionId(IdeActions.ACTION_FIND));
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

  public static @NotNull String getGotoNextSplitTerminalActionText(boolean forward) {
    return forward ? ActionsBundle.message("action.NextSplitter.text")
                   : ActionsBundle.message("action.PrevSplitter.text");
  }

  public @NotNull TerminalAction getGotoNextSplitTerminalAction(@Nullable JBTerminalWidgetListener listener, boolean forward) {
    String actionId = forward ? "Terminal.NextSplitter" : "Terminal.PrevSplitter";
    String text = UIUtil.removeMnemonic(getGotoNextSplitTerminalActionText(forward));
    return new TerminalAction(new TerminalActionPresentation(text, getKeyStrokesByActionId(actionId)), event -> {
      if (listener != null) {
        listener.gotoNextSplitTerminal(forward);
      }
      return true;
    });
  }

  private static @NotNull List<KeyStroke> getKeyStrokesByActionId(@NotNull String actionId) {
    List<KeyStroke> keyStrokes = new ArrayList<>();
    Shortcut[] shortcuts = getActiveKeymapShortcuts(actionId).getShortcuts();
    for (Shortcut sc : shortcuts) {
      if (sc instanceof KeyboardShortcut) {
        KeyStroke ks = ((KeyboardShortcut)sc).getFirstKeyStroke();
        keyStrokes.add(ks);
      }
    }
    return keyStrokes;
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

  @Override
  public @NotNull TerminalActionPresentation getNextTabActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.SelectNextTab.text"), getKeyStrokesByActionId("NextTab"));
  }

  @Override
  public @NotNull TerminalActionPresentation getPreviousTabActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.SelectPreviousTab.text"),
                                          getKeyStrokesByActionId("PreviousTab"));
  }

  public @NotNull TerminalActionPresentation getMoveTabRightActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.MoveRight.text"),
                                          getKeyStrokesByActionId("Terminal.MoveToolWindowTabRight"));
  }

  public @NotNull TerminalActionPresentation getMoveTabLeftActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.MoveLeft.text"),
                                          getKeyStrokesByActionId("Terminal.MoveToolWindowTabLeft"));
  }

  public @NotNull TerminalActionPresentation getShowTabsActionPresentation() {
    return new TerminalActionPresentation(IdeBundle.message("terminal.action.ShowTabs.text"),
                                          getKeyStrokesByActionId(ShowContentAction.ACTION_ID));
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
