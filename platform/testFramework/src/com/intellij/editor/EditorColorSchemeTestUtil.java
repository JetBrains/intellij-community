// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editor;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.JavaXmlDocumentKt;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Editor color scheme helpers for a JUnit 5 test.
 * This is the replacement of {@link EditorColorSchemeTestCase}.
 */
@TestOnly
public final class EditorColorSchemeTestUtil {
  private EditorColorSchemeTestUtil() {
  }

  /**
   * Parses {@code docText} as a scheme XML and reads it into a new scheme with the default scheme as the parent.
   * Call it from any thread.
   */
  public static @NotNull EditorColorsScheme loadScheme(@NotNull String docText) throws IOException, SAXException {
    Document doc = JavaXmlDocumentKt.createDocumentBuilder().parse(new InputSource(new StringReader(docText)));
    Element root = new DOMBuilder().build(doc.getDocumentElement());

    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getDefaultScheme();
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);

    targetScheme.readExternal(root);

    return targetScheme;
  }

  /**
   * Sets {@code attributes} for {@code key} in a copy of the default scheme, writes the copy, and reads it into a new scheme.
   * Returns the new scheme and its attributes for {@code key}.
   * Call it from any thread.
   */
  public static @NotNull Pair<EditorColorsScheme, TextAttributes> doTestWriteRead(@NotNull TextAttributesKey key,
                                                                                  @NotNull TextAttributes attributes) {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getDefaultScheme();

    EditorColorsScheme sourceScheme = (EditorColorsScheme)defaultScheme.clone();
    sourceScheme.setName("test");
    sourceScheme.setAttributes(key, attributes);

    Element root = new Element("scheme");
    ((AbstractColorsScheme)sourceScheme).writeExternal(root);

    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);
    targetScheme.readExternal(root);
    assertEquals("test", targetScheme.getName());
    TextAttributes targetAttrs = targetScheme.getAttributes(key);
    return Pair.create(targetScheme, targetAttrs);
  }

  /**
   * Checks that {@code root} gives the {@code expected} XML text.
   * Call it from any thread.
   */
  public static void assertXmlOutputEquals(String expected, @NotNull Element root) {
    assertEquals(expected, JDOMUtil.write(root));
  }

  /**
   * Writes {@code scheme} without the platform-specific font options and without the meta info.
   * Call it from any thread.
   */
  public static @NotNull Element serialize(@NotNull EditorColorsScheme scheme) {
    Element root = new Element("scheme");
    ((AbstractColorsScheme)scheme).writeExternal(root);
    fixPlatformSpecificValues(root);
    root.removeChildren("metaInfo");
    return root;
  }

  /**
   * Writes {@code scheme} without the platform-specific font options.
   * The meta info keeps only the values of the rainbow properties.
   * Call it from any thread.
   */
  public static @NotNull Element serializeWithFixedMeta(@NotNull EditorColorsScheme scheme) {
    Element root = new Element("scheme");
    ((AbstractColorsScheme)scheme).writeExternal(root);
    fixPlatformSpecificValues(root);
    Element metaInfo = root.getChild("metaInfo");
    if (metaInfo != null) {
      metaInfo.getChildren().forEach((child) -> {
        Attribute name = child.getAttribute("name");
        if (!child.getName().equals("property")
            || name == null
            || !RainbowHighlighter.isRainbowKey(name.getValue())) {
          child.removeContent();
        }
      });
    }
    return root;
  }

  /**
   * Writes {@code scheme} without the platform-specific font options.
   * The meta info keeps only the {@code properties}.
   * Call it from any thread.
   */
  public static @NotNull Element serializeWithSelectedMetaInfo(@NotNull AbstractColorsScheme scheme, String... properties) {
    Element root = new Element("scheme");
    scheme.writeExternal(root);
    fixPlatformSpecificValues(root);
    Element metaInfo = root.getChild("metaInfo");
    if (metaInfo == null) {
      return root;
    }

    List<Element> toRemove = new ArrayList<>();
    for (Element element : metaInfo.getChildren()) {
      Attribute name = element.getAttribute("name");
      boolean result = false;
      for (String t : properties) {
        if (t.equals(name.getValue())) {
          result = true;
          break;
        }
      }
      if (!element.getName().equals("property") || name == null || !result) {
        toRemove.add(element);
      }
    }
    for (Element child : toRemove) {
      metaInfo.removeContent(child);
    }
    return root;
  }

  private static void fixPlatformSpecificValues(@NotNull Element root) {
    List<Element> fontOptions = new ArrayList<>(root.getChildren("option"));
    for (Element option : fontOptions) {
      String name = option.getAttributeValue("name");
      if (name != null) {
        if ("FONT_SCALE".equals(name) ||
            "EDITOR_FONT_SIZE".equals(name) ||
            "EDITOR_FONT_NAME".equals(name)) {
          root.removeContent(option);
        }
        else if ("CONSOLE_FONT_NAME".equals(name)) {
          option.setAttribute("value", "Test");
        }
      }
    }
  }
}
