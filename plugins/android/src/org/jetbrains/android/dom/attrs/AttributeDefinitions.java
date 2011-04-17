/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.attrs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class AttributeDefinitions {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.attrs.AttributeDefinitions");

  private Map<String, AttributeDefinition> myAttrs = new HashMap<String, AttributeDefinition>();
  private Map<String, StyleableDefinition> myStyleables = new HashMap<String, StyleableDefinition>();

  private final List<StyleableDefinition> myStateStyleables = new ArrayList<StyleableDefinition>();

  public AttributeDefinitions() {
  }

  public AttributeDefinitions(@NotNull XmlFile... files) {
    for (XmlFile file : files) {
      addAttrsFromFile(file);
    }
  }

  private void addAttrsFromFile(XmlFile file) {
    Map<StyleableDefinition, String[]> parentMap = new HashMap<StyleableDefinition, String[]>();
    final XmlDocument document = file.getDocument();
    if (document == null) return;
    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null || !"resources".equals(rootTag.getName())) return;
    for (XmlTag tag : rootTag.getSubTags()) {
      String tagName = tag.getName();
      if (tagName.equals("attr")) {
        parseAttrTag(tag);
      }
      else if (tagName.equals("declare-styleable")) {
        parseDeclareStyleableTag(tag, parentMap);
      }
    }

    for (Map.Entry<StyleableDefinition, String[]> entry : parentMap.entrySet()) {
      StyleableDefinition definition = entry.getKey();
      String[] parentNames = entry.getValue();
      for (String parentName : parentNames) {
        StyleableDefinition parent = getStyleableByName(parentName);
        if (parent != null) {
          definition.addParent(parent);
          parent.addChild(definition);
        }
        else {
          LOG.info("Found tag with unknown parent: " + parentName);
        }
      }
    }
  }

  @Nullable
  private AttributeDefinition parseAttrTag(XmlTag tag) {
    String name = tag.getAttributeValue("name");
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return null;
    }
    List<AttributeFormat> parsedFormats;
    List<AttributeFormat> formats = new ArrayList<AttributeFormat>();
    String format = tag.getAttributeValue("format");
    if (format != null) {
      parsedFormats = parseAttrFormat(format);
      if (parsedFormats != null) formats.addAll(parsedFormats);
    }
    XmlTag[] values = tag.findSubTags("enum");
    if (values.length > 0) {
      formats.add(AttributeFormat.Enum);
    }
    else {
      values = tag.findSubTags("flag");
      if (values.length > 0) {
        formats.add(AttributeFormat.Flag);
      }
    }
    AttributeDefinition def = myAttrs.get(name);
    if (def == null) {
      def = new AttributeDefinition(name);
      myAttrs.put(def.getName(), def);
    }
    def.addFormats(formats);
    parseAndAddValues(def, values);
    return def;
  }

  private static List<AttributeFormat> parseAttrFormat(String formatString) {
    List<AttributeFormat> result = new ArrayList<AttributeFormat>();
    final String[] formats = formatString.split("\\|");
    for (String format : formats) {
      final AttributeFormat attributeFormat;
      try {
        attributeFormat = AttributeFormat.valueOf(StringUtil.capitalize(format));
      }
      catch (IllegalArgumentException e) {
        return null;
      }
      result.add(attributeFormat);
    }
    return result;
  }

  private static void parseAndAddValues(AttributeDefinition def, XmlTag[] values) {
    for (XmlTag value : values) {
      final String valueName = value.getAttributeValue("name");
      if (valueName == null) {
        LOG.info("Unknown value for tag: " + value.getText());
      }
      else {
        def.addValue(valueName);
      }
    }
  }

  private void parseDeclareStyleableTag(XmlTag tag, Map<StyleableDefinition, String[]> parentMap) {
    String name = tag.getAttributeValue("name");
    if (name == null) {
      LOG.info("Found declare-styleable tag with no name: " + tag.getText());
      return;
    }
    StyleableDefinition def = new StyleableDefinition(name);
    String parentNameAttributeValue = tag.getAttributeValue("parent");
    if (parentNameAttributeValue != null) {
      String[] parentNames = parentNameAttributeValue.split("\\s+");
      parentMap.put(def, parentNames);
    }
    myStyleables.put(name, def);

    if (name.endsWith("State")) {
      myStateStyleables.add(def);
    }

    for (XmlTag subTag : tag.findSubTags("attr")) {
      parseStyleableAttr(def, subTag);
    }
  }

  private void parseStyleableAttr(StyleableDefinition def, XmlTag tag) {
    String name = tag.getAttributeValue("name");
    if (name == null) {
      LOG.info("Found attr tag with no name: " + tag.getText());
      return;
    }
    AttributeDefinition attr = myAttrs.get(name);
    if (attr == null) {
      attr = parseAttrTag(tag);
    }
    if (attr != null) {
      def.addAttribute(attr);
    }
  }

  public void addStyleable(@NotNull StyleableDefinition styleable) {
    myStyleables.put(styleable.getName(), styleable);
  }

  public void addAttrDef(@NotNull AttributeDefinition attr) {
    myAttrs.put(attr.getName(), attr);
  }

  @Nullable
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    return myStyleables.get(name);
  }

  public Set<String> getAttributeNames() {
    return myAttrs.keySet();
  }

  @Nullable
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    return myAttrs.get(name);
  }

  @NotNull
  public Set<String> getStyleableNames() {
    return myStyleables.keySet();
  }

  public StyleableDefinition[] getStateStyleables() {
    return myStateStyleables.toArray(new StyleableDefinition[myStateStyleables.size()]);
  }
}
