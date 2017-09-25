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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.impl.ExtensionDomExtender;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.util.List;

public class PluginDescriptorStructureUtil {
  private static final Logger LOG = Logger.getInstance(PluginDescriptorStructureUtil.class);
  private static final int LOCATION_MAX_LENGTH = 40;

  private PluginDescriptorStructureUtil() {
  }


  @NotNull
  public static String getTagDisplayText(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return DevKitBundle.message("error.plugin.xml.tag.invalid");
    }

    if (element instanceof Action) {
      String actionId = ((Action)element).getId().getStringValue();
      if (StringUtil.isNotEmpty(actionId)) {
        return actionId;
      }
    }
    else if (element instanceof ExtensionPoint) {
      ExtensionPoint epElement = (ExtensionPoint)element;
      String epName = epElement.getName().getStringValue();
      if (StringUtil.isNotEmpty(epName)) {
        return epName;
      }
      String epInterface = epElement.getInterface().getStringValue();
      if (StringUtil.isNotEmpty(epInterface)) {
        //noinspection ConstantConditions
        return toShortName(epInterface);
      }
      String epBeanClass = epElement.getBeanClass().getStringValue();
      if (StringUtil.isNotEmpty(epBeanClass)) {
        //noinspection ConstantConditions
        return toShortName(epBeanClass);
      }
    }

    return toHumanReadableName(element.getXmlElementName()); // default
  }

  @Nullable
  public static Icon getTagIcon(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return null;
    }

    if (element instanceof Action) {
      XmlAttributeValue iconAttrValue = ((Action)element).getIcon().getXmlAttributeValue();
      if (iconAttrValue != null) {
        for (PsiReference reference : iconAttrValue.getReferences()) {
          Icon icon = getIconFromReference(reference);
          if (icon != null) {
            return icon;
          }
        }
      }
    }
    else if (element instanceof Group) {
      return AllIcons.Actions.GroupByPackage;
    }

    return AllIcons.Nodes.Tag;
  }

  @Nullable
  public static String getTagLocationString(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return null;
    }

    String result;
    if (element instanceof IdeaVersion) {
      result = getIdeaVersionLocation((IdeaVersion)element);
    }
    else if (element instanceof Extensions) {
      result = getExtensionsLocation((Extensions)element);
    }
    else if (element instanceof ExtensionPoint) {
      result = getExtensionPointLocation((ExtensionPoint)element);
    }
    else if (element instanceof Component) {
      result = getComponentLocation((Component)element);
    }
    else if (element instanceof Group) {
      result = getGroupLocation((Group)element);
    }
    else if (element instanceof AddToGroup) {
      result = getAddToGroupLocation((AddToGroup)element);
    }
    else if (element instanceof Extension) {
      result = getExtensionLocation(element);
    }
    else if (element.getParent() instanceof Action && "keyboard-shortcut".equalsIgnoreCase(element.getXmlElementName())) {
      result = getKeyboardShortcutLocation(element);
    }
    else if (element instanceof Vendor) {
      result = getVendorLocation((Vendor)element);
    }
    else if (element.getParent() instanceof IdeaPlugin && element instanceof GenericDomValue) {
      result = getTopLevelNodeLocation(element);
    }
    else {
      result = guessTagLocation(element);
    }

    return shorten(result);
  }


  @Nullable
  private static String getIdeaVersionLocation(IdeaVersion element) {
    String since = element.getSinceBuild().getStringValue();
    if (StringUtil.isNotEmpty(since)) {
      String until = element.getUntilBuild().getStringValue();
      return since + "-" + (StringUtil.isNotEmpty(until) ? until : "...");
    }
    return null;
  }

  @Nullable
  private static String getExtensionsLocation(Extensions element) {
    return element.getDefaultExtensionNs().getStringValue();
  }

  @Nullable
  private static String getExtensionPointLocation(ExtensionPoint element) {
    String result = null;
    String epName = element.getName().getStringValue();
    if (StringUtil.isNotEmpty(epName)) {
      String epInterface = element.getInterface().getStringValue();
      if (StringUtil.isNotEmpty(epInterface)) {
        result = toShortName(epInterface);
      }
      String epBeanClass = element.getBeanClass().getStringValue();
      if (StringUtil.isNotEmpty(epBeanClass)) {
        result = toShortName(epBeanClass);
      }
    }
    return result;
  }

  @Nullable
  private static String getComponentLocation(Component element) {
    String implementationClassText = element.getImplementationClass().getRawText();
    return toShortName(implementationClassText);
  }

  @Nullable
  private static String getGroupLocation(Group element) {
    return element.getId().getStringValue();
  }

  @Nullable
  private static String getAddToGroupLocation(AddToGroup element) {
    return element.getGroupId().getStringValue();
  }

  @Nullable
  private static String getExtensionLocation(DomElement element) {
    String elementName = element.getXmlElementName();
    if (elementName.equalsIgnoreCase("application-service") || elementName.equalsIgnoreCase("project-service") ||
        elementName.equalsIgnoreCase("module-service")) {
      String result = ((Extension)element).getId().getStringValue();
      if (StringUtil.isEmpty(result)) {
        result = toShortName(firstNotNullAttribute(element, "serviceInterface", "serviceImplementation"));
      }
      return result;
    }
    else if (elementName.equalsIgnoreCase("intentionAction")) {
      return toShortName(getSubTagText(element, "className"));
    }
    else if (elementName.equalsIgnoreCase("dom.extender")) {
      String result = ((Extension)element).getId().getStringValue();
      if (StringUtil.isEmpty(result)) {
        result = toShortName(firstNotNullAttribute(element, "extenderClass"));
      }
      return result;
    }
    else if (elementName.equalsIgnoreCase("stacktrace.fold")) {
      return firstNotNullAttribute(element, "substring");
    }
    else {
      return guessTagLocation(element);
    }
  }

  @Nullable
  private static String getKeyboardShortcutLocation(DomElement element) {
    return firstNotNullAttribute(element, "first-keystroke");
  }

  @NotNull
  private static String getVendorLocation(Vendor element) {
    return element.getValue();
  }

  @Nullable
  private static String getTopLevelNodeLocation(DomElement element) {
    String tagName = element.getXmlElementName();
    if (tagName.equalsIgnoreCase("id") || tagName.equalsIgnoreCase("name") || tagName.equalsIgnoreCase("version") ||
        tagName.equalsIgnoreCase("category") || tagName.equalsIgnoreCase("resource-bundle")) {
      return ((GenericDomValue)element).getRawText();
    }
    if (tagName.equalsIgnoreCase("depends")) {
      String result = ((GenericDomValue)element).getRawText();
      if ("true".equalsIgnoreCase(firstNotNullAttribute(element, "optional"))) {
        result += " [optional]";
      }
      return result;
    }

    return null;
  }

  @Nullable
  private static String guessTagLocation(DomElement element) {
    String location = null;

    // avoid displaying location equal to display text for actions and EPs
    if (!(element instanceof ExtensionPoint) && !(element instanceof Action)) {
      location = firstNotNullAttribute(element, "id", "name", "displayName", "shortName");
    }
    if (location == null) {
      location = toShortName(firstNotNullAttribute(
        element, "instance", "class", "implementation", "implementationClass", "interface", "interfaceClass"));
    }
    if (location == null) {
      location = firstNotNullAttribute(element, "file");
    }

    if (location != null) {
      return location;
    }

    List<? extends DomAttributeChildDescription> descriptions = element.getGenericInfo().getAttributeChildrenDescriptions();
    String possibleOnlyValue = null;
    for (DomAttributeChildDescription description : descriptions) {
      String value = description.getDomAttributeValue(element).getStringValue();
      if (StringUtil.isEmpty(value)) {
        continue;
      }
      if (possibleOnlyValue == null) {
        possibleOnlyValue = value;
      }
      else {
        // more than one attribute
        possibleOnlyValue = null;
        break;
      }
    }

    if (possibleOnlyValue != null && StringUtil.countChars(possibleOnlyValue, '.') > 2) {
      possibleOnlyValue = toShortName(possibleOnlyValue);
    }

    return possibleOnlyValue;
  }


  @Nullable
  private static String toShortName(@Nullable String fqName) {
    if (fqName == null || fqName.contains(" ")) {
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
    String result = tagName.replaceAll("-", " ").replaceAll("\\.", " | ");
    result = StringUtil.join(NameUtil.nameToWords(result), " ");

    result = StringUtil.capitalizeWords(result, true)
      .replaceAll("Psi", "PSI")
      .replaceAll("Dom", "DOM")
      .replaceAll("Sdk", "SDK")
      .replaceAll("Junit", "JUnit")
      .replaceAll("Idea", "IDEA")
      .replaceAll("Javaee", "JavaEE")
      .replaceAll("Jsf", "JSF")
      .replaceAll("Mvc", "MVC")
      .replaceAll("El", "EL")
      .replaceAll("Id", "ID");

    return result;
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
  private static String firstNotNullAttribute(DomElement element, String... attributes) {
    DomGenericInfo genericInfo = element.getGenericInfo();
    for (String attribute : attributes) {
      DomAttributeChildDescription description = genericInfo.getAttributeChildDescription(attribute);
      if (description == null) {
        continue;
      }

      String value = description.getDomAttributeValue(element).getStringValue();
      if (StringUtil.isNotEmpty(value)) {
        return value;
      }
    }

    return null;
  }

  @Nullable
  private static String getSubTagText(DomElement element, @SuppressWarnings("SameParameterValue") String subTagName) {
    DomFixedChildDescription subTagDescription = element.getGenericInfo().getFixedChildDescription(subTagName);
    if (subTagDescription == null) {
      return null;
    }
    return subTagDescription.getValues(element).stream()
      .filter(e -> e instanceof ExtensionDomExtender.SimpleTagValue)
      .map(e -> (ExtensionDomExtender.SimpleTagValue)e)
      .map(ExtensionDomExtender.SimpleTagValue::getTagValue)
      .findAny()
      .orElse(null);
  }

  @Nullable
  private static DomElement getDomElement(@Nullable XmlTag tag) {
    if (tag == null) {
      return null;
    }
    Project project = tag.getProject();
    return DomManager.getDomManager(project).getDomElement(tag);
  }

  @Nullable
  private static Icon getIconFromReference(@NotNull PsiReference reference) {
    PsiElement resolved = reference.resolve();
    if (!(resolved instanceof PsiField)) {
      return null;
    }
    UField field = UastContextKt.toUElement(resolved, UField.class);
    if (field == null) {
      LOG.error("Cannot convert PsiField to UField: " + resolved);
      return null;
    }

    UExpression expression = field.getUastInitializer();
    if (expression == null) {
      return null;
    }

    ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(resolved.getProject());
    VirtualFile iconFile = iconsAccessor.resolveIconFile(expression.getPsi());
    return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
  }
}
