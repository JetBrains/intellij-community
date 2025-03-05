// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.intellij.structuralsearch.MatchOptions.CASE_SENSITIVE_ATTRIBUTE_NAME;
import static com.intellij.structuralsearch.MatchOptions.OLD_CASE_SENSITIVE_ATTRIBUTE_NAME;

/**
 * @author Bas Leijdekkers
 */
public final class ConfigurationUtil {
  private ConfigurationUtil() {}

  public static @NotNull String toXml(@NotNull Configuration configuration) {
    configuration = configuration.copy();
    configuration.getMatchOptions().setScope(null); // don't export scope
    final String className = configuration.getClass().getSimpleName();
    final Element element = new Element(Character.toLowerCase(className.charAt(0)) + className.substring(1));
    configuration.writeExternal(element);
    if (Boolean.parseBoolean(element.getAttributeValue(OLD_CASE_SENSITIVE_ATTRIBUTE_NAME))) {
      element.setAttribute(CASE_SENSITIVE_ATTRIBUTE_NAME, "true");
    }
    element.removeAttribute(OLD_CASE_SENSITIVE_ATTRIBUTE_NAME);
    return JDOMUtil.writeElement(element);
  }

  public static Configuration fromXml(@NotNull String xml) throws JDOMException {
    xml = xml.trim();
    final Configuration configuration;
    if (xml.startsWith("<searchConfiguration ") && xml.endsWith("</searchConfiguration>")) {
      configuration = new SearchConfiguration();
    }
    else if (xml.startsWith("<replaceConfiguration ") && xml.endsWith("</replaceConfiguration>")) {
      configuration = new ReplaceConfiguration();
    }
    else {
      return null;
    }
    try {
      final Element element = JDOMUtil.load(xml);
      configuration.readExternal(element);
      return configuration;
    }
    catch (IOException ignored) {
      // can't happen
      return null;
    }
  }
}
