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
package org.jetbrains.idea.devkit.navigation.structure;

import com.google.common.base.Joiner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;

public class PluginDescriptorStructureUtil {
  private static final int LOCATION_MAX_LENGTH = 50;

  private PluginDescriptorStructureUtil() {
  }


  @NotNull
  public static String getTagDisplayText(@Nullable XmlTag tag) {
    if (tag == null) {
      return DevKitBundle.message("error.plugin.xml.tag.invalid");
    }

    @NonNls String tagName = tag.getLocalName();
    if (tagName.equalsIgnoreCase("id")) {
      return "ID";
    }

    if (tagName.equalsIgnoreCase("action")) {
      String actionId = tag.getAttributeValue("id");
      if (actionId != null) {
        return actionId;
      }
    }
    else if (tagName.equalsIgnoreCase("extensionPoint")) {
      String displayText = tag.getAttributeValue("name");
      if (displayText != null) {
        return displayText;
      }
      displayText = toShortName(firstNotNullAttribute(tag, "interface", "beanClass"));
      if (displayText != null) {
        return displayText;
      }
    }

    return toHumanReadableName(tagName); // default
  }

  @Nullable
  public static String getTagLocationString(@Nullable XmlTag tag) {
    if (tag == null) {
      return null;
    }

    String tagName = tag.getLocalName();
    String result = null;
    if (tagName.equalsIgnoreCase("id") || tagName.equalsIgnoreCase("name") || tagName.equalsIgnoreCase("version") ||
        tagName.equalsIgnoreCase("category") || tagName.equalsIgnoreCase("vendor") || tagName.equalsIgnoreCase("depends") ||
        tagName.equalsIgnoreCase("resource-bundle")) {
      result = tag.getValue().getText();
    }
    else if (tagName.equalsIgnoreCase("idea-version")) {
      String sinceBuild = tag.getAttributeValue("since-build");
      if (sinceBuild != null) {
        String untilBuild = tag.getAttributeValue("until-build");
        result = sinceBuild + "-" + (untilBuild != null ? untilBuild : "...");
      }
    }
    else if (tagName.equalsIgnoreCase("extensions")) {
      result = tag.getAttributeValue("defaultExtensionNs");
    }
    else if (tagName.equalsIgnoreCase("component")) {
      result = toShortName(tag.getSubTagText("implementation-class"));
    }
    else if (tagName.equalsIgnoreCase("group")) {
      result = tag.getAttributeValue("id");
    }
    else if (tagName.equalsIgnoreCase("add-to-group")) {
      result = tag.getAttributeValue("group-id");
    }
    else if (tagName.equalsIgnoreCase("applicationService") || tagName.equalsIgnoreCase("projectService") ||
             tagName.equalsIgnoreCase("moduleService")) {
      result = tag.getAttributeValue("id");
      if (result == null) {
        result = toShortName(firstNotNullAttribute(tag, "serviceInterface", "serviceImplementation"));
      }
    }
    else if (tagName.equalsIgnoreCase("intentionAction")) {
      result = toShortName(tag.getSubTagText("className"));
    }
    else {
      result = guessTagLocation(tag);
    }

    return shorten(result);
  }

  @Nullable
  public static Icon getTagIcon(@Nullable XmlTag tag) {
    if (tag == null) {
      return null;
    }

    String tagName = tag.getLocalName();
    if (tagName.equalsIgnoreCase("action")) {
      String iconPath = tag.getAttributeValue("icon");
      if (iconPath != null) {
        Icon icon = IconLoader.findIcon(iconPath);
        if (icon != null) {
          return icon;
        }
      }
    }
    if (tagName.equalsIgnoreCase("group")) {
      return AllIcons.Actions.GroupByPackage;
    }

    return AllIcons.Nodes.Tag;
  }


  @Nullable
  private static String firstNotNullAttribute(@NotNull XmlTag tag, @NotNull String... attributes) {
    for (String attribute : attributes) {
      String value = tag.getAttributeValue(attribute);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @Nullable
  private static String shorten(@Nullable String text) {
    if (text == null) {
      return null;
    }
    if (text.length() <= LOCATION_MAX_LENGTH) {
      return text;
    }
    return StringUtil.trimMiddle(text, LOCATION_MAX_LENGTH);
  }

  @Nullable
  private static String toShortName(@Nullable String fqName) {
    if (fqName == null) {
      return null;
    }
    String shortName = StringUtil.substringAfterLast(fqName, ".");
    if (shortName != null) {
      return shortName;
    }
    return fqName;
  }

  @NotNull
  private static String toHumanReadableName(@NotNull String tagName) {
    String result;
    if (tagName.contains("-") || tagName.contains(".")) {
      result = Joiner.on(" ").join(tagName.split("[-.]"));
    } else {
      result = StringUtil.splitCamelCase(tagName);
    }

    result = StringUtil.capitalizeWords(result, true)
      .replaceAll("Psi", "PSI")
      .replaceAll("Sdk", "SDK")
      .replaceAll("Junit", "JUnit");

    return result;
  }

  @Nullable
  private static String guessTagLocation(XmlTag tag) {
    String tagName = tag.getLocalName();
    String location = null;

    // avoid displaying location equal to display text for actions and EPs
    if (!tagName.equalsIgnoreCase("extensionPoint") && !tagName.equalsIgnoreCase("action")) {
      location = firstNotNullAttribute(tag, "id", "name", "displayName", "shortName");
    }

    if (location == null) {
      location = toShortName(firstNotNullAttribute(
        tag, "instance", "class", "implementation", "implementationClass", "interface", "interfaceClass"));
    }

    return location;
  }
}
