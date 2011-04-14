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
package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animation.AnimationElement;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.android.dom.color.ColorDomElement;
import org.jetbrains.android.dom.color.ColorStateListItem;
import org.jetbrains.android.dom.converters.CompositeConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.drawable.*;
import org.jetbrains.android.dom.layout.Fragment;
import org.jetbrains.android.dom.layout.Include;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestElement;
import org.jetbrains.android.dom.menu.MenuElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.Intent;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDomExtender extends DomExtender<AndroidDomElement> {
  private static final String[] LAYOUT_ATTRIBUTES_SUFS = new String[]{"_Layout", "_MarginLayout", "_Cell"};
  private static final String ANDROID_NS_PREFIX = "http://schemas.android.com/apk/res/";

  @Nullable
  private static String getNamespaceKeyByResourcePackage(@NotNull AndroidFacet facet, @Nullable String resPackage) {
    if (resPackage == null) {
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        String aPackage = manifest.getPackage().getValue();
        if (aPackage != null && aPackage.length() > 0) {
          return ANDROID_NS_PREFIX + aPackage;
        }
      }
    }
    else if (resPackage.equals(SYSTEM_RESOURCE_PACKAGE)) {
      return SdkConstants.NS_RESOURCES;
    }
    return null;
  }

  protected static void registerStyleableAttributes(DomElement element,
                                                    @NotNull StyleableDefinition[] styleables,
                                                    @Nullable String namespace,
                                                    DomExtensionsRegistrar registrar,
                                                    MyAttributeProcessor processor,
                                                    String... skipNames) {
    Set<String> skippedAttrSet = new HashSet<String>();
    Collections.addAll(skippedAttrSet, skipNames);
    XmlAttribute[] existingAttrs = element.getXmlTag().getAttributes();
    for (XmlAttribute attr : existingAttrs) {
      if (attr.getNamespace().equals(namespace)) {
        skippedAttrSet.add(attr.getLocalName());
      }
    }
    for (StyleableDefinition styleable : styleables) {
      for (AttributeDefinition attrDef : styleable.getAttributes()) {
        String attrName = attrDef.getName();
        if (!skippedAttrSet.contains(attrName)) {
          skippedAttrSet.add(attrName);
          registerAttribute(attrDef, namespace, registrar, processor, element);
        }
      }
    }
  }

  private static boolean mustBeSoft(@NotNull Converter converter, Collection<AttributeFormat> formats) {
    if (converter instanceof CompositeConverter || converter instanceof ResourceReferenceConverter) {
      return false;
    }
    return formats.size() > 1;
  }

  private interface MyAttributeProcessor {
    void process(@NotNull XmlName attrName, @NotNull DomExtension extension, @NotNull DomElement element);
  }

  private static void registerAttribute(@NotNull AttributeDefinition attrDef,
                                        String namespaceKey,
                                        DomExtensionsRegistrar registrar,
                                        @Nullable MyAttributeProcessor processor,
                                        @NotNull DomElement element) {
    XmlName xmlName = new XmlName(attrDef.getName(), namespaceKey);
    Set<AttributeFormat> formats = attrDef.getFormats();
    Class valueClass = formats.size() == 1 ? getValueClass(formats.iterator().next()) : String.class;
    registrar.registerAttributeChildExtension(xmlName, GenericAttributeValue.class);
    DomExtension extension = registrar.registerGenericAttributeValueChildExtension(xmlName, valueClass);
    final Converter converter = AndroidDomUtil.getConverter(attrDef);
    if (converter != null) {
      extension.setConverter(converter, mustBeSoft(converter, attrDef.getFormats()));
    }
    if (processor != null) {
      processor.process(xmlName, extension, element);
    }
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           DomExtensionsRegistrar registrar,
                                           MyAttributeProcessor processor) {
    registerAttributes(facet, element, styleableNames, null, registrar, processor);
    registerAttributes(facet, element, styleableNames, SYSTEM_RESOURCE_PACKAGE, registrar, processor);
  }

  private static StyleableDefinition[] getStyleables(@NotNull AttributeDefinitions definitions, @NotNull String[] names) {
    List<StyleableDefinition> styleables = new ArrayList<StyleableDefinition>();
    for (String name : names) {
      StyleableDefinition styleable = definitions.getStyleableByName(name);
      if (styleable != null) {
        styleables.add(styleable);
      }
    }
    return styleables.toArray(new StyleableDefinition[styleables.size()]);
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String styleableName,
                                           @Nullable String resPackage,
                                           DomExtensionsRegistrar registrar,
                                           String... skipNames) {
    registerAttributes(facet, element, new String[]{styleableName}, resPackage, registrar, null, skipNames);
  }

  protected static void registerAttributes(AndroidFacet facet,
                                           DomElement element,
                                           @NotNull String[] styleableNames,
                                           @Nullable String resPackage,
                                           DomExtensionsRegistrar registrar,
                                           MyAttributeProcessor processor,
                                           String... skipNames) {
    ResourceManager manager = facet.getResourceManager(resPackage);
    if (manager == null) return;
    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) return;
    StyleableDefinition[] styleables = getStyleables(attrDefs, styleableNames);
    registerAttributes(facet, element, styleables, resPackage, registrar, processor, skipNames);
  }

  private static void registerAttributes(AndroidFacet facet,
                                         DomElement element,
                                         StyleableDefinition[] styleables, String resPackage,
                                         DomExtensionsRegistrar registrar,
                                         MyAttributeProcessor processor,
                                         String... skipNames) {
    String namespace = getNamespaceKeyByResourcePackage(facet, resPackage);
    registerStyleableAttributes(element, styleables, namespace, registrar, processor, skipNames);
  }

  @NotNull
  private static Class getValueClass(@Nullable AttributeFormat format) {
    if (format == null) return String.class;
    switch (format) {
      case Boolean:
        return boolean.class;
      /*case Integer:
        return int.class;*/
      case Reference:
      case Dimension:
      case Color:
        return ResourceValue.class;
      default:
        return String.class;
    }
  }

  protected static void registerAttributesForClassAndSuperclasses(AndroidFacet facet,
                                                                  DomElement element,
                                                                  PsiClass c,
                                                                  DomExtensionsRegistrar registrar,
                                                                  MyAttributeProcessor processor) {
    while (c != null) {
      String styleableName = c.getName();
      if (styleableName != null) {
        registerAttributes(facet, element, new String[]{styleableName}, registrar, processor);
      }
      c = getSuperclass(c);
    }
  }

  @Nullable
  protected static PsiClass getSuperclass(@NotNull final PsiClass c) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      public PsiClass compute() {
        return c.getSuperClass();
      }
    });
  }

  private static boolean isPreference(@NotNull Map<String, PsiClass> preferenceClassMap, @NotNull PsiClass c) {
    PsiClass preferenceClass = preferenceClassMap.get("Preference");
    return preferenceClass != null && (preferenceClass == c || c.isInheritor(preferenceClass, true));
  }

  public static void registerExtensionsForXmlResources(AndroidFacet facet,
                                                       String tagName,
                                                       XmlResourceElement element,
                                                       DomExtensionsRegistrar registrar,
                                                       Set<String> registeredSubtags) {
    String styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (styleableName != null) {
      String[] attrsToSkip = element instanceof Intent ? new String[]{"action"} : ArrayUtil.EMPTY_STRING_ARRAY;
      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, registrar, attrsToSkip);
    }

    if (tagName.equals("searchable")) {
      registerSubtags("actionkey", XmlResourceElement.class, registrar, registeredSubtags);
    }

    // for keyboard api
    if (tagName.equals("Keyboard")) {
      registerSubtags("Row", XmlResourceElement.class, registrar, registeredSubtags);
    }
    else if (tagName.equals("Row")) {
      registerSubtags("Key", XmlResourceElement.class, registrar, registeredSubtags);
    }

    // for device-admin api
    if (tagName.equals("device-admin")) {
      registerSubtags("uses-policies", XmlResourceElement.class, registrar, registeredSubtags);
    }
    else if (tagName.equals("uses-policies")) {
      registerSubtags("limit-password", XmlResourceElement.class, registrar, registeredSubtags);
      registerSubtags("watch-login", XmlResourceElement.class, registrar, registeredSubtags);
      registerSubtags("reset-password", XmlResourceElement.class, registrar, registeredSubtags);
      registerSubtags("force-lock", XmlResourceElement.class, registrar, registeredSubtags);
      registerSubtags("wipe-data", XmlResourceElement.class, registrar, registeredSubtags);
    }

    // for preferences
    Map<String, PsiClass> prefClassMap = getPreferencesClassMap(facet);
    String prefClassName = element.getXmlTag().getName();
    PsiClass c = prefClassMap.get(prefClassName);

    // register attributes by preference class
    registerAttributesForClassAndSuperclasses(facet, element, c, registrar, null);

    //register attributes by widget
    String suffix = "Preference";
    if (prefClassName.endsWith(suffix)) {
      String widgetClassName = prefClassName.substring(0, prefClassName.length() - suffix.length());
      Map<String, PsiClass> viewClassMap = getViewClassMap(facet);
      PsiClass widgetClass = viewClassMap.get(widgetClassName);
      registerAttributesForClassAndSuperclasses(facet, element, widgetClass, registrar, null);
    }

    if (c != null && isPreference(prefClassMap, c)) {
      for (String subtagName : prefClassMap.keySet()) {
        registerSubtags(subtagName, PreferenceElement.class, registrar, registeredSubtags);
      }
    }
  }

  @NotNull
  public static Map<String, PsiClass> getPreferencesClassMap(@NotNull AndroidFacet facet) {
    return facet.getClassMap(AndroidUtils.PREFERENCE_CLASS_NAME, SimpleClassMapConstructor.getInstance());
  }

  public static void registerExtensionsForAnimation(AndroidFacet facet,
                                                    String tagName,
                                                    AnimationElement element,
                                                    DomExtensionsRegistrar registrar,
                                                    Set<String> registeredSubtags) {
    if (tagName.equals("set")) {
      for (String subtagName : AndroidAnimationUtils.getPossibleChildren(facet)) {
        registerSubtags(subtagName, AnimationElement.class, registrar, registeredSubtags);
      }
    }
    final String styleableName = AndroidAnimationUtils.getStyleableNameByTagName(tagName);
    PsiClass c = facet.findClass(AndroidUtils.ANIMATION_PACKAGE + '.' + styleableName);
    if (c != null) {
      registerAttributesForClassAndSuperclasses(facet, element, c, registrar, null);
    }
    else {
      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, registrar);
      String layoutAnim = "LayoutAnimation";
      if (styleableName.endsWith(layoutAnim) && !styleableName.equals(layoutAnim)) {
        registerAttributes(facet, element, layoutAnim, SYSTEM_RESOURCE_PACKAGE, registrar);
      }
      if (styleableName.endsWith("Animation")) {
        registerAttributes(facet, element, "Animation", SYSTEM_RESOURCE_PACKAGE, registrar);
      }
    }
  }

  public static Map<String, PsiClass> getViewClassMap(@NotNull AndroidFacet facet) {
    return facet.getClassMap(AndroidUtils.VIEW_CLASS_NAME, SimpleClassMapConstructor.getInstance());
  }

  private static String[] getClassNames(@NotNull Collection<PsiClass> classes) {
    List<String> names = new ArrayList<String>();
    for (PsiClass aClass : classes) {
      names.add(aClass.getName());
    }
    return ArrayUtil.toStringArray(names);
  }

  private static void registerLayoutAttributes(AndroidFacet facet,
                                               DomElement element,
                                               XmlTag tag,
                                               DomExtensionsRegistrar registrar,
                                               MyAttributeProcessor processor) {
    XmlTag parentTag = tag.getParentTag();
    Map<String, PsiClass> map = getViewClassMap(facet);
    if (parentTag != null) {
      PsiClass c = map.get(parentTag.getName());
      while (c != null) {
        registerLayoutAttributes(facet, element, c, registrar, processor);
        c = getSuperclass(c);
      }
    }
    else {
      for (String className : map.keySet()) {
        PsiClass c = map.get(className);
        registerLayoutAttributes(facet, element, c, registrar, processor);
      }
    }
  }

  private static void registerLayoutAttributes(AndroidFacet facet,
                                               DomElement element,
                                               PsiClass c,
                                               DomExtensionsRegistrar registrar,
                                               MyAttributeProcessor processor) {
    String styleableName = c.getName();
    if (styleableName != null) {
      for (String suf : LAYOUT_ATTRIBUTES_SUFS) {
        registerAttributes(facet, element, new String[]{styleableName + suf}, registrar, processor);
      }
    }
  }

  private static final MyAttributeProcessor ourLayoutAttrsProcessor = new MyAttributeProcessor() {
    @Override
    public void process(@NotNull XmlName attrName, @NotNull DomExtension extension, @NotNull DomElement element) {
      if ((element instanceof LayoutViewElement || element instanceof Fragment) &&
          SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
        XmlElement xmlElement = element.getXmlElement();
        XmlTag tag = xmlElement instanceof XmlTag ? (XmlTag)xmlElement : null;
        String tagName = tag != null ? tag.getName() : null;
        if (!"merge".equals(tagName) && (tag == null || tag.getAttribute("style") == null)) {
          XmlTag parentTag = tag != null ? tag.getParentTag() : null;
          String parentTagName = parentTag != null ? parentTag.getName() : null;
          if (!"TableRow".equals(parentTagName) && !"TableLayout".equals(parentTagName) &&
              ("layout_width".equals(attrName.getLocalName()) || "layout_height".equals(attrName.getLocalName()))) {
            extension.addCustomAnnotation(new MyRequired());
          }
        }
      }
    }
  };

  private static class MyRequired implements Required {
    public boolean value() {
      return true;
    }

    public boolean nonEmpty() {
      return true;
    }

    public boolean identifier() {
      return false;
    }

    public Class<? extends Annotation> annotationType() {
      return Required.class;
    }
  }

  public static void registerExtensionsForLayout(AndroidFacet facet,
                                                 XmlTag tag,
                                                 LayoutElement element,
                                                 DomExtensionsRegistrar registrar,
                                                 Set<String> registeredSubtags) {
    Map<String, PsiClass> map = getViewClassMap(facet);
    if (element instanceof Include) {
      for (String className : map.keySet()) {
        PsiClass c = map.get(className);
        registerLayoutAttributes(facet, element, c, registrar, ourLayoutAttrsProcessor);
      }
      return;
    }
    else if (element instanceof Fragment) {
      registerAttributes(facet, element, new String[]{"Fragment"}, registrar, ourLayoutAttrsProcessor);
    }
    else {
      String tagName = tag.getName();
      if (!tagName.equals("view")) {
        PsiClass c = map.get(tagName);
        registerAttributesForClassAndSuperclasses(facet, element, c, registrar, ourLayoutAttrsProcessor);
      }
      else {
        String[] styleableNames = getClassNames(map.values());
        registerAttributes(facet, element, styleableNames, registrar, ourLayoutAttrsProcessor);
      }
    }

    registerLayoutAttributes(facet, element, tag, registrar, ourLayoutAttrsProcessor);

    for (String viewClassName : map.keySet()) {
      PsiClass viewClass = map.get(viewClassName);
      if (!AndroidUtils.isAbstract(viewClass)) {
        registerSubtags(viewClassName, LayoutViewElement.class, registrar, registeredSubtags);
      }
    }
    //registrar.registerFixedNumberChildExtension(new XmlName("requestFocus"), LayoutElement.class);
  }

  public static void registerExtensionsForManifest(AndroidFacet facet,
                                                   String tagName,
                                                   ManifestElement element,
                                                   DomExtensionsRegistrar registrar,
                                                   Set<String> registeredSubtags) {
    String styleableName = AndroidManifestUtils.getStyleableNameByTagName(tagName);
    String[] skipNames = AndroidManifestUtils.getStaticallyDefinedAttrs(element);
    SystemResourceManager manager = facet.getSystemResourceManager();
    if (manager == null) return;
    AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
    if (attrDefs == null) return;
    StyleableDefinition styleable = attrDefs.getStyleableByName(styleableName);
    if (styleable == null) return;
    registerStyleableAttributes(element, new StyleableDefinition[]{styleable}, SdkConstants.NS_RESOURCES, registrar, null, skipNames);

    Set<String> subtagSet = new HashSet<String>();
    Collections.addAll(subtagSet, AndroidManifestUtils.getStaticallyDefinedSubtags(element));
    for (StyleableDefinition child : styleable.getChildren()) {
      String childTagName = AndroidManifestUtils.getTagNameByStyleableName(child.getName());
      if (childTagName != null && !subtagSet.contains(childTagName)) {
        Class c = AndroidManifestUtils.getClassByManifestStyleableName(child.getName());
        if (c != null) {
          registerSubtags(childTagName, c, registrar, registeredSubtags);
        }
      }
    }
  }

  public void registerExtensions(@NotNull AndroidDomElement element, @NotNull DomExtensionsRegistrar registrar) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) return;
    XmlTag tag = element.getXmlTag();
    registerExistingAttributes(facet, tag, registrar, element);
    String tagName = tag.getName();
    Set<String> registeredSubtags = new HashSet<String>();
    if (element instanceof ManifestElement) {
      registerExtensionsForManifest(facet, tagName, (ManifestElement)element, registrar, registeredSubtags);
    }
    else if (element instanceof LayoutElement) {
      registerExtensionsForLayout(facet, tag, (LayoutElement)element, registrar, registeredSubtags);
    }
    else if (element instanceof AnimationElement) {
      registerExtensionsForAnimation(facet, tagName, (AnimationElement)element, registrar, registeredSubtags);
    }
    else if (element instanceof MenuElement) {
      String styleableName = StringUtil.capitalize(tagName);
      if (!styleableName.equals("Menu")) {
        styleableName = "Menu" + styleableName;
      }
      registerAttributes(facet, element, styleableName, SYSTEM_RESOURCE_PACKAGE, registrar);
    }
    else if (element instanceof XmlResourceElement) {
      registerExtensionsForXmlResources(facet, tagName, (XmlResourceElement)element, registrar, registeredSubtags);
    }
    else if (element instanceof DrawableDomElement || element instanceof ColorDomElement) {
      registerExtensionsForStateList(facet, tagName, element, registrar);
    }
    Collections.addAll(registeredSubtags, AndroidDomUtil.getStaticallyDefinedSubtags(element));

    if (!(element instanceof LayoutElement) &&
        !(element instanceof ColorDomElement) &&
        (!(element instanceof DrawableDomElement) || element instanceof UnknownDrawableDomElement)) {
      Processor<String> existingSubtagsFilter = element instanceof XmlResourceElement ?
                                                new Processor<String>() {
                                                  public boolean process(String s) {
                                                    return s.length() > 0 && Character.isLowerCase(s.charAt(0));
                                                  }
                                                } : null;
      registerExistingSubtags(tag, registrar, registeredSubtags, existingSubtagsFilter);
    }
  }

  private static void registerExtensionsForStateList(AndroidFacet facet,
                                                     String tagName,
                                                     AndroidDomElement element,
                                                     DomExtensionsRegistrar registrar) {
    final String specialStyleableName = AndroidDrawableDomUtil.SPECIAL_STYLEABLE_NAMES.get(tagName);
    if (specialStyleableName != null) {
      registerAttributes(facet, element, specialStyleableName, SYSTEM_RESOURCE_PACKAGE, registrar);
    }

    if (element instanceof DrawableStateListItem || element instanceof ColorStateListItem) {
      registerAttributes(facet, element, "DrawableStates", SYSTEM_RESOURCE_PACKAGE, registrar);

      final AttributeDefinitions attrDefs = getAttrDefs(facet);
      if (attrDefs != null) {
        registerAttributes(facet, element, attrDefs.getStateStyleables(), SYSTEM_RESOURCE_PACKAGE, registrar, null);
      }
    }
  }

  @Nullable
  private static AttributeDefinitions getAttrDefs(AndroidFacet facet) {
    final SystemResourceManager manager = facet.getSystemResourceManager();
    return manager != null ? manager.getAttributeDefinitions() : null;
  }

  private static void registerSubtags(@NotNull String name, Type type, DomExtensionsRegistrar registrar, Set<String> registeredTags) {
    registrar.registerCollectionChildrenExtension(new XmlName(name), type);
    registeredTags.add(name);
  }

  private static void registerExistingSubtags(XmlTag tag,
                                              DomExtensionsRegistrar registrar,
                                              Set<String> skipNames,
                                              @Nullable Processor<String> filter) {
    XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      String localName = subtag.getLocalName();
      if (!skipNames.contains(localName) && (filter == null || filter.process(localName))) {
        if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
          registrar.registerCollectionChildrenExtension(new XmlName(localName), AndroidDomElement.class);
        }
      }
    }
  }

  private static void registerExistingAttributes(AndroidFacet facet,
                                                 XmlTag tag,
                                                 DomExtensionsRegistrar registrar,
                                                 AndroidDomElement element) {
    XmlAttribute[] attrs = tag.getAttributes();
    for (XmlAttribute attr : attrs) {
      String localName = attr.getLocalName();
      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if (!"xmlns".equals(attr.getNamespacePrefix())) {
          AttributeDefinition attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr);
          if (attrDef == null) {
            attrDef = new AttributeDefinition(localName);
          }
          String namespace = attr.getNamespace();
          registerAttribute(attrDef, namespace.length() > 0 ? namespace : null, registrar, null, element);
        }
      }
    }
  }
}
