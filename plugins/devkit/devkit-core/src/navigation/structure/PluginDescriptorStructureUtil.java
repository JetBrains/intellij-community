// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.impl.ExtensionDomExtender;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PluginDescriptorStructureUtil {
  public static final Icon DEFAULT_ICON = AllIcons.Nodes.Tag;

  private static final Set<String> KNOWN_TOP_LEVEL_NODE_NAMES =
    ContainerUtil.immutableSet("id", "name", "version", "category", "resource-bundle");

  private static final Map<String, String> TAG_DISPLAY_NAME_REPLACEMENTS = new ContainerUtil.ImmutableMapBuilder<String, String>()
    .put("psi", "PSI")
    .put("dom", "DOM")
    .put("sdk", "SDK")
    .put("junit", "JUnit")
    .put("idea", "IDEA")
    .put("javaee", "JavaEE")
    .put("jsf", "JSF")
    .put("mvc", "MVC")
    .put("el", "EL")
    .put("id", "ID")
    .put("jsp", "JSP")
    .put("xml", "XML")
    .put("ast", "AST")
    .put("gdsl", "GDSL")
    .put("pom", "POM")
    .put("html", "HTML")
    .put("php", "PHP")
    .build();

  private PluginDescriptorStructureUtil() {
  }

  public static @NotNull String getTagDisplayText(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return safeGetTagDisplayText(tag);
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
      String epQualifiedName = epElement.getQualifiedName().getStringValue();
      if (StringUtil.isNotEmpty(epQualifiedName)) {
        //noinspection ConstantConditions
        return toShortName(epQualifiedName);
      }
    }
    else if (element instanceof Separator) {
      return "----------";
    }

    return toDisplayName(element.getXmlElementName()); // default
  }

  public static @NotNull String safeGetTagDisplayText(@Nullable XmlTag tag) {
    return tag != null ? toDisplayName(tag.getLocalName()) : DevKitBundle.message("error.plugin.xml.tag.invalid");
  }

  public static @Nullable Icon getTagIcon(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return tag != null ? DEFAULT_ICON : null;
    }

    return ObjectUtils.notNull(ElementPresentationManager.getIcon(element), DEFAULT_ICON);
  }

  public static @Nullable String getTagLocationString(@Nullable XmlTag tag) {
    DomElement element = getDomElement(tag);
    if (element == null) {
      return null;
    }

    if (element instanceof PluginModule) {
      return getPluginModuleLocation((PluginModule)element);
    }
    if (element instanceof IdeaVersion) {
      return getIdeaVersionLocation((IdeaVersion)element);
    }
    if (element instanceof Extensions) {
      return getExtensionsLocation((Extensions)element);
    }
    if (element instanceof ExtensionPoints) {
      return getExtensionPointsLocation(element);
    }
    if (element instanceof ExtensionPoint) {
      return getExtensionPointLocation((ExtensionPoint)element);
    }
    if (element instanceof With) {
      return getWithLocation((With)element);
    }
    if (element instanceof Component) {
      return getComponentLocation((Component)element);
    }
    if (element instanceof Group) {
      return getGroupLocation((Group)element);
    }
    if (element instanceof AddToGroup) {
      return getAddToGroupLocation((AddToGroup)element);
    }
    if (element instanceof Extension) {
      return getExtensionLocation((Extension)element);
    }
    if (element instanceof KeyboardShortcut) {
      return getKeyboardShortcutLocation((KeyboardShortcut)element);
    }
    if (element instanceof Vendor) {
      return getVendorLocation((Vendor)element);
    }
    if (element.getParent() instanceof IdeaPlugin && element instanceof GenericDomValue) {
      return getTopLevelNodeLocation((GenericDomValue<?>)element);
    }

    return guessTagLocation(element);
  }

  private static String getPluginModuleLocation(PluginModule pluginModule) {
    return pluginModule.getValue().getStringValue();
  }

  private static @Nullable String getIdeaVersionLocation(IdeaVersion element) {
    String since = element.getSinceBuild().getStringValue();
    if (StringUtil.isNotEmpty(since)) {
      String until = element.getUntilBuild().getStringValue();
      return since + " - " + (StringUtil.isNotEmpty(until) ? until : "...");
    }
    return null;
  }

  private static @Nullable String getExtensionsLocation(Extensions element) {
    return element.getDefaultExtensionNs().getStringValue();
  }

  private static @Nullable String getExtensionPointsLocation(DomElement element) {
    DomElement parent = element.getParent();
    if (parent instanceof IdeaPlugin) {
      return ((IdeaPlugin)parent).getPluginId();
    }
    return null;
  }

  private static @Nullable String getExtensionPointLocation(ExtensionPoint element) {
    String epInterface = element.getInterface().getStringValue();
    if (StringUtil.isNotEmpty(epInterface)) {
      return toShortName(epInterface);
    }
    String epBeanClass = element.getBeanClass().getStringValue();
    if (StringUtil.isNotEmpty(epBeanClass)) {
      return toShortName(epBeanClass);
    }
    return null;
  }

  private static @Nullable String getWithLocation(With element) {
    return element.getAttribute().getStringValue();
  }

  private static @Nullable String getComponentLocation(Component element) {
    String implementationClassText = element.getImplementationClass().getRawText();
    return toShortName(implementationClassText);
  }

  private static @Nullable String getGroupLocation(ActionOrGroup element) {
    return element.getId().getStringValue();
  }

  private static @Nullable String getAddToGroupLocation(AddToGroup element) {
    return element.getGroupId().getStringValue();
  }

  private static @Nullable String getExtensionLocation(Extension element) {
    DomElement parent = element.getParent();
    if (parent instanceof Extensions) {
      String extensionsNamespace = ((Extensions)parent).getDefaultExtensionNs().getStringValue();
      if (Extensions.DEFAULT_PREFIX.equals(extensionsNamespace)) {
        String elementName = element.getXmlElementName();
        if (elementName.equalsIgnoreCase("applicationService") ||
            elementName.equalsIgnoreCase("projectService") ||
            elementName.equalsIgnoreCase("moduleService")) {
          String result = element.getId().getStringValue();
          if (StringUtil.isEmpty(result)) {
            result = toShortName(firstNotNullAttribute(element, "serviceInterface", "serviceImplementation"));
          }
          return result;
        }
        else if (elementName.equalsIgnoreCase("intentionAction")) {
          return toShortName(getSubTagText(element, "className"));
        }
        else if (elementName.equalsIgnoreCase("dom.extender")) {
          String result = element.getId().getStringValue();
          if (StringUtil.isEmpty(result)) {
            result = toShortName(firstNotNullAttribute(element, "extenderClass"));
          }
          return result;
        }
        else if (elementName.equalsIgnoreCase("stacktrace.fold")) {
          return firstNotNullAttribute(element, "substring");
        }
      }
    }

    return guessTagLocation(element);
  }

  private static @Nullable String getKeyboardShortcutLocation(KeyboardShortcut element) {
    return element.getFirstKeystroke().getStringValue();
  }

  private static @NotNull String getVendorLocation(Vendor element) {
    return element.getValue();
  }

  private static @Nullable String getTopLevelNodeLocation(GenericDomValue<?> element) {
    if (element instanceof Dependency) {
      Dependency dependency = (Dependency)element;
      String result = dependency.getRawText();

      Boolean optional = dependency.getOptional().getValue();
      if (optional != null && optional) {
        result += " [optional]";
      }
      return result;
    }

    if (KNOWN_TOP_LEVEL_NODE_NAMES.contains(StringUtil.toLowerCase(element.getXmlElementName()))) {
      return element.getRawText();
    }

    return null;
  }

  private static @Nullable String guessTagLocation(DomElement element) {
    String location = toShortName(firstNotNullAttribute(
      element, "instance", "class", "implementation", "implementationClass", "interface", "interfaceClass"));

    // avoid displaying location equal to display text for actions
    if (location == null && !(element instanceof Action)) {
      location = firstNotNullAttribute(element, "id", "name", "displayName", "shortName");
    }
    if (location == null) {
      location = firstNotNullAttribute(element, "file");
    }

    if (location != null) {
      return location;
    }

    DomGenericInfo genericInfo = element.getGenericInfo();
    List<? extends DomAttributeChildDescription<?>> attrDescriptions = genericInfo.getAttributeChildrenDescriptions();
    String possibleOnlyAttrValue = null;
    for (DomAttributeChildDescription<?> description : attrDescriptions) {
      String value = description.getDomAttributeValue(element).getStringValue();
      if (StringUtil.isEmpty(value)) {
        continue;
      }
      if (possibleOnlyAttrValue == null) {
        possibleOnlyAttrValue = value;
      }
      else {
        // more than one attribute
        possibleOnlyAttrValue = null;
        break;
      }
    }

    if (possibleOnlyAttrValue != null && StringUtil.countChars(possibleOnlyAttrValue, '.') > 2) {
      possibleOnlyAttrValue = toShortName(possibleOnlyAttrValue);
    }

    if (possibleOnlyAttrValue != null) {
      return possibleOnlyAttrValue;
    }

    // check if tag doesn't have attributes and subtags and use it's text content as a location in such cases
    if (attrDescriptions.isEmpty() && genericInfo.getFixedChildrenDescriptions().isEmpty()) {
      if (element instanceof GenericDomValue) {
        return ((GenericDomValue<?>)element).getRawText();
      }
      /*if (element instanceof ExtensionDomExtender.SimpleTagValue) {
        return ((ExtensionDomExtender.SimpleTagValue)element).getStringValue();
      }*/
    }

    return null;
  }


  private static @Nullable String toShortName(@Nullable String fqName) {
    if (fqName == null || fqName.contains(" ")) {
      return null;
    }
    String shortName = StringUtil.substringAfterLast(fqName, ".");
    if (shortName != null) {
      return shortName;
    }
    return fqName;
  }

  private static @NotNull String toDisplayName(@NotNull String tagName) {
    String result = tagName.replaceAll("-", " ").replaceAll("\\.", "|");

    String[] words = NameUtil.nameToWords(result);
    for (int i = 0; i < words.length; i++) {
      @NonNls String word = words[i];
      String replacement = TAG_DISPLAY_NAME_REPLACEMENTS.get(StringUtil.toLowerCase(word));
      if (replacement != null) {
        words[i] = replacement;
      }
    }

    result = StringUtil.join(words, " ");
    result = StringUtil.capitalizeWords(result, true);

    return result;
  }

  private static @Nullable String firstNotNullAttribute(DomElement element, String... attributes) {
    DomGenericInfo genericInfo = element.getGenericInfo();
    for (String attribute : attributes) {
      DomAttributeChildDescription<?> description = genericInfo.getAttributeChildDescription(attribute);
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

  private static @Nullable String getSubTagText(DomElement element, @SuppressWarnings("SameParameterValue") String subTagName) {
    DomFixedChildDescription subTagDescription = element.getGenericInfo().getFixedChildDescription(subTagName);
    if (subTagDescription == null) {
      return null;
    }
    return StreamEx.of(subTagDescription.getValues(element))
      .select(ExtensionDomExtender.SimpleTagValue.class)
      .map(ExtensionDomExtender.SimpleTagValue::getStringValue)
      .findAny()
      .orElse(null);
  }

  private static @Nullable DomElement getDomElement(@Nullable XmlTag tag) {
    if (tag == null) {
      return null;
    }
    Project project = tag.getProject();
    return DomManager.getDomManager(project).getDomElement(tag);
  }
}
