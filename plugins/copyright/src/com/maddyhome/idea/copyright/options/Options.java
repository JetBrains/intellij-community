/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 */
public class Options implements Cloneable {
  public LanguageOptions getOptions(String name) {
    String lang = FileTypeUtil.getInstance().getFileTypeNameByName(name);
    LanguageOptions res = options.get(lang);
    if (res == null) {
      // NOTE: If any change is made here you need to update ConfigTabFactory and UpdateCopyrightFactory too.
      final FileType fileType = FileTypeUtil.getInstance().getFileTypeByName(name);
      if (fileType != null) {
        final UpdateCopyrightsProvider provider = CopyrightUpdaters.INSTANCE.forFileType(fileType);
        if (provider != null) return provider.getDefaultOptions();
      }
      res = new LanguageOptions();
    }

    return res;
  }

  public LanguageOptions getTemplateOptions() {
    return getOptions(LANG_TEMPLATE);
  }

  public void setOptions(String name, LanguageOptions options) {
    String lang = FileTypeUtil.getInstance().getFileTypeNameByName(name);
    this.options.put(lang, options);
  }

  public void setTemplateOptions(LanguageOptions options) {
    setOptions(LANG_TEMPLATE, options);
  }

  @Nullable
  public LanguageOptions getMergedOptions(String name) {
    try {
      LanguageOptions lang = getOptions(name).clone();
      LanguageOptions temp = getTemplateOptions().clone();
      switch (lang.getFileTypeOverride()) {
        case LanguageOptions.USE_TEMPLATE:
          temp.setFileLocation(lang.getFileLocation());
          temp.setFileTypeOverride(lang.getFileTypeOverride());
          lang = temp;
          break;
        case LanguageOptions.USE_TEXT:
          break;
      }

      return lang;
    }
    catch (CloneNotSupportedException e) {
      // This shouldn't happen
    }

    return null;
  }


  public void readExternal(Element element) throws InvalidDataException {
    logger.debug("readExternal()");
    List<Element> languageOptions = element.getChildren("LanguageOptions");
    if (languageOptions != null && !languageOptions.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < languageOptions.size(); i++) {
        Element languageOption = languageOptions.get(i);
        // NOTE: If any change is made here you need to update ConfigTabFactory and UpdateCopyrightFactory too.
        LanguageOptions opts = new LanguageOptions();
        opts.readExternal(languageOption);
        setOptions(languageOption.getAttributeValue("name"), opts);
      }
    }
    else {
      Element root = null;
      Element jOpts = element.getChild("JavaOptions");
      if (jOpts != null) // version 2.1.x
      {
        root = jOpts;
      }
      else // versions 0.0.1 - 2.0.x
      {
        Element child = element.getChild("option");
        if (child != null && child.getAttribute("name") != null) {
          root = element;
        }
      }
      if (root != null) {
        String languageName = StdFileTypes.JAVA.getName();
        // NOTE: If any change is made here you need to update ConfigTabFactory and UpdateCopyrightFactory too.
        LanguageOptions opts = new LanguageOptions();
        opts.setFileTypeOverride(LanguageOptions.USE_TEMPLATE);
        for (Object option : root.getChildren("option")) {
          String name = ((Element)option).getAttributeValue("name");
          String val = ((Element)option).getAttributeValue("value");
          if ("body".equals(name)) {
            //todo opts.setNotice(val);
          }
          else if ("location".equals(name)) {
            opts.setFileLocation(Integer.parseInt(val));
          }
        }

        setOptions(languageName, opts);
      }
    }

    logger.debug("options=" + this);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    logger.debug("writeExternal()");

    for (String lang : options.keySet()) {
      Element elem = new Element("LanguageOptions");
      elem.setAttribute("name", lang);
      element.addContent(elem);
      options.get(lang).writeExternal(elem);
    }

    logger.debug("options=" + this);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Options options1 = (Options)o;

    return options.equals(options1.options);
  }

  public int hashCode() {
    int result;
    result = options.hashCode();
    return result;
  }

  public String toString() {
    return "Options" + "{options=" + options + '}';
  }

  @Override
  public Options clone() throws CloneNotSupportedException {
    Options res = (Options)super.clone();
    res.options = new TreeMap<>();
    for (String lang : options.keySet()) {
      LanguageOptions opts = options.get(lang);
      res.options.put(lang, opts.clone());
    }

    return res;
  }

  private Map<String, LanguageOptions> options = new TreeMap<>();
  private static final String LANG_TEMPLATE = "__TEMPLATE__";

  private static final Logger logger = Logger.getInstance(Options.class.getName());
}