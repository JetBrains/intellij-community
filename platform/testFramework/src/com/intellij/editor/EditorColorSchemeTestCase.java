// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public abstract class EditorColorSchemeTestCase extends LightPlatformTestCase {
  protected static EditorColorsScheme loadScheme(@NotNull String docText) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
    InputSource inputSource = new InputSource(new StringReader(docText));
    Document doc = docBuilder.parse(inputSource);
    Element root = new DOMBuilder().build(doc.getDocumentElement());

    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    EditorColorsScheme targetScheme = new EditorColorsSchemeImpl(defaultScheme);

    targetScheme.readExternal(root);

    return targetScheme;
  }

  @NotNull
  protected Pair<EditorColorsScheme, TextAttributes> doTestWriteRead(@NotNull TextAttributesKey key, @NotNull TextAttributes attributes) {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);

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

  public static void assertXmlOutputEquals(String expected, Element root) {
    assertEquals(expected, JDOMUtil.write(root));
  }

  protected Element serialize(@NotNull EditorColorsScheme scheme) {
    Element root = new Element("scheme");
    ((AbstractColorsScheme)scheme).writeExternal(root);
    fixPlatformSpecificValues(root);
    root.removeChildren("metaInfo");
    return root;
  }

  protected Element serializeWithFixedMeta(@NotNull EditorColorsScheme scheme) {
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
