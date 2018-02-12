/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.importer.colors;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeImportException;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UseJBColor")
public class EclipseThemeReader extends DefaultHandler implements EclipseColorThemeElements {
  @Nullable private final OptionHandler myOptionHandler;
  @Nullable private String myThemeName;
  
  private static final Map<String, Integer> ECLIPSE_DEFAULT_FONT_STYLES = new HashMap<>();
  static {
    ECLIPSE_DEFAULT_FONT_STYLES.put(KEYWORD_TAG, Font.BOLD);
  }

  public EclipseThemeReader(@Nullable OptionHandler handler) {
    myOptionHandler = handler;
  }


  void readSettings(InputStream input) throws SchemeImportException {
    SAXParserFactory spf = SAXParserFactory.newInstance();
    spf.setValidating(false);
    try {
      SAXParser parser = spf.newSAXParser();
      parser.parse(input, this);
    }
    catch (Exception e) {
      if (e.getCause() instanceof NotAnEclipseThemeException) {
        throw new SchemeImportException("The input file is not a valid Eclipse theme.");
      }
      else {
        throw new SchemeImportException(e);
      }
    }
  }
  
  private static class NotAnEclipseThemeException extends Exception {
    NotAnEclipseThemeException(String message) {
      super(message);
    }
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (COLOR_THEME_TAG.equals(qName)) {
      myThemeName = attributes.getValue(NAME_ATTR);
      if (myThemeName == null) {
        throw new SAXException(new NotAnEclipseThemeException("'name' attribute of 'colorTheme' is missing."));
      }
    }
    else {
      if (myThemeName != null) {
        if (myOptionHandler != null) {
          myOptionHandler.handleColorOption(qName, readTextAttributes(qName, attributes, isBackground(qName)));
        }
      }
      else {
        throw new SAXException(new NotAnEclipseThemeException("'colorTheme' element is missing."));
      }
    }
  }
  
  public interface OptionHandler {
    void handleColorOption(@NotNull String name, @NotNull TextAttributes textAttribute);
  }
  
  private static TextAttributes readTextAttributes(@NotNull String tag, Attributes attributes, boolean isBackground) throws SAXException {
    TextAttributes textAttributes = new TextAttributes();
    Color color = getColor(attributes);
    if (isBackground)  {
      textAttributes.setBackgroundColor(color);
    }
    else {
      textAttributes.setForegroundColor(color);
    }
    textAttributes.setFontType(getFontStyle(tag, attributes));
    EffectType effectType = getEffectType(attributes);
    if (effectType != null) {
      textAttributes.setEffectType(effectType);
      textAttributes.setEffectColor(textAttributes.getForegroundColor());
    }
    return textAttributes;
  }
  
  private static boolean isBackground(@NotNull String tagName) {
    if (tagName.length() >= BACKGROUND_SUFFIX.length()) {
      tagName = tagName.substring(tagName.length() - BACKGROUND_SUFFIX.length());
      return tagName.equalsIgnoreCase(BACKGROUND_SUFFIX);
    }
    return false;
  }

  @Nullable
  public String getThemeName() {
    return myThemeName;
  }
  
  @Nullable
  private static Color getColor(Attributes attributes) throws SAXException {
    String colorString = attributes.getValue(COLOR_ATTR);
    if (colorString != null && !colorString.isEmpty()) {
      try {
        int colorValue = Integer.decode(colorString);
        return new Color(colorValue);
      }
      catch (NumberFormatException nfe) {
        throw new SAXException("Invalid color value: '" + colorString + "'");
      }
    }
    return null;
  }

  @JdkConstants.FontStyle
  private static int getFontStyle(@NotNull String tag, @NotNull Attributes attributes) {
    int fontStyle = getDefaultFontStyle(tag);
    String boldValue = attributes.getValue(BOLD_ATTR);
    if (boldValue != null) {
      if (Boolean.parseBoolean(boldValue)) fontStyle |= Font.BOLD;
      else fontStyle &= ~Font.BOLD;
    }
    String italicValue = attributes.getValue(ITALIC_ATTR);
    if (italicValue != null) {
      if (Boolean.parseBoolean(italicValue)) fontStyle |= Font.ITALIC;
      else fontStyle &= ~Font.ITALIC;
    }
    return fontStyle;
  }
  
  @JdkConstants.FontStyle
  private static int getDefaultFontStyle(@NotNull String tag) {
    //noinspection MagicConstant
    return ECLIPSE_DEFAULT_FONT_STYLES.getOrDefault(tag, Font.PLAIN);
  }
  
  @Nullable
  private static EffectType getEffectType(@NotNull Attributes attributes) {
    String strikeThrough = attributes.getValue(STRIKETHROUGH_ATTR);
    if (Boolean.parseBoolean(strikeThrough)) {
      return EffectType.STRIKEOUT;
    }
    String underline = attributes.getValue(UNDERLINE_ATTR);
    if (Boolean.parseBoolean(underline)) {
      return EffectType.LINE_UNDERSCORE;
    }
    return null;
  }
}
