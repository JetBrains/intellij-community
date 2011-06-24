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
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.converters.*;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.dom.menu.Group;
import org.jetbrains.android.dom.menu.Item;
import org.jetbrains.android.dom.menu.Menu;
import org.jetbrains.android.dom.resources.*;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
public class AndroidDomUtil {
  public static final StaticEnumConverter BOOLEAN_CONVERTER = new StaticEnumConverter("true", "false");
  public static final Map<String, String> SPECIAL_RESOURCE_TYPES = new HashMap<String, String>();
  private static final PackageClassConverter ACTIVITY_CONVERTER = new PackageClassConverter(AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
  private static final OnClickConverter ON_CLICK_CONVERTER = new OnClickConverter();

  static {
    addSpecialResourceType("string", "label", "description", "title");
    addSpecialResourceType("drawable", "icon");
    addSpecialResourceType("style", "theme");
    addSpecialResourceType("anim", "animation");
    addSpecialResourceType("id", "id");
  }

  private AndroidDomUtil() {
  }

  @Nullable
  public static String getResourceType(@NotNull AttributeFormat format) {
    switch (format) {
      case Color:
        return "color";
      case Dimension:
        return "dimen";
      case String:
        return "string";
      case Integer:
        return "integer";
      case Boolean:
        return "bool";
      default:
        return null;
    }
  }

  @Nullable
  public static ResolvingConverter<String> getStringConverter(@NotNull AttributeFormat format, @NotNull String[] values) {
    switch (format) {
      case Enum:
        return new StaticEnumConverter(values);
      case Boolean:
        return BOOLEAN_CONVERTER;
      case Integer:
        return IntegerConverter.INSTANCE;
      default:
        return null;
    }
  }

  @Nullable
  public static ResourceReferenceConverter getResourceReferenceConverter(@NotNull AttributeDefinition attr) {
    boolean containsReference = false;
    Set<String> resourceTypes = new HashSet<String>();
    Set<AttributeFormat> formats = attr.getFormats();
    for (AttributeFormat format : formats) {
      if (format == AttributeFormat.Reference) {
        containsReference = true;
      }
      String type = getResourceType(format);
      if (type != null) {
        resourceTypes.add(type);
      }
    }
    String specialResourceType = getSpecialResourceType(attr.getName());
    if (specialResourceType != null) {
      resourceTypes.add(specialResourceType);
    }
    if (containsReference) {
      if (resourceTypes.contains("color")) resourceTypes.add("drawable");
      if (resourceTypes.size() == 0) {
        resourceTypes.addAll(ResourceManager.REFERABLE_RESOURCE_TYPES);
      }
    }
    if (resourceTypes.size() > 0) {
      return new ResourceReferenceConverter(resourceTypes);
    }
    return null;
  }

  @Nullable
  public static ResolvingConverter<String> simplify(CompositeConverter composite) {
    switch (composite.size()) {
      case 0:
        return null;
      case 1:
        return composite.getConverters().get(0);
      default:
        return composite;
    }
  }

  @Nullable
  public static Converter getSpecificConverter(@NotNull XmlName attrName, DomElement context) {
    if (context == null) {
      return null;
    }

    if (!SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
      return null;
    }

    final XmlTag xmlTag = context.getXmlTag();
    if (xmlTag == null) {
      return null;
    }

    final String localName = attrName.getLocalName();
    final String tagName = xmlTag.getName();

    if (context instanceof XmlResourceElement) {
      if ("configure".equals(localName) && "appwidget-provider".equals(tagName)) {
        return ACTIVITY_CONVERTER;
      }
    }
    else if (context instanceof LayoutViewElement) {
      if ("onClick".equals(localName)) {
        return ON_CLICK_CONVERTER;
      }
    }

    return null;
  }

  @Nullable
  public static ResolvingConverter getConverter(@NotNull AttributeDefinition attr) {
    Set<AttributeFormat> formats = attr.getFormats();
    CompositeConverter composite = new CompositeConverter();
    String[] values = attr.getValues();
    for (AttributeFormat format : formats) {
      ResolvingConverter<String> converter = getStringConverter(format, values);
      if (converter != null) {
        composite.addConverter(converter);
      }
    }
    ResourceReferenceConverter resConverter = getResourceReferenceConverter(attr);
    if (formats.contains(AttributeFormat.Flag)) {
      if (resConverter != null) {
        composite.addConverter(new LightFlagConverter(values));
      }
      return new FlagConverter(simplify(composite), values);
    }
    ResolvingConverter<String> stringConverter = simplify(composite);
    if (resConverter != null) {
      resConverter.setAdditionalConverter(simplify(composite));
      return resConverter;
    }
    return stringConverter;
  }

  @Nullable
  public static String getSpecialResourceType(String attrName) {
    String type = SPECIAL_RESOURCE_TYPES.get(attrName);
    if (type != null) return type;
    if (attrName.endsWith("Animation")) return "anim";
    return null;
  }

  // for special cases
  static void addSpecialResourceType(String type, String... attrs) {
    for (String attr : attrs) {
      SPECIAL_RESOURCE_TYPES.put(attr, type);
    }
  }

  public static boolean containsAction(@NotNull IntentFilter filter, @NotNull String name) {
    for (Action action : filter.getActions()) {
      if (name.equals(action.getName().getValue())) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsCategory(@NotNull IntentFilter filter, @NotNull String name) {
    for (Category category : filter.getCategories()) {
      if (name.equals(category.getName().getValue())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static Activity getActivityDomElementByClass(@NotNull Module module, PsiClass c) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        Application application = manifest.getApplication();
        if (application != null) {
          for (Activity activity : application.getActivities()) {
            PsiClass activityClass = activity.getActivityClass().getValue();
            if (c.getManager().areElementsEquivalent(c, activityClass)) {
              return activity;
            }
          }
        }
      }
    }
    return null;
  }

  public static String[] getStaticallyDefinedSubtags(@NotNull AndroidDomElement element) {
    if (element instanceof ManifestElement) {
      return AndroidManifestUtils.getStaticallyDefinedSubtags((ManifestElement)element);
    }
    if (element instanceof LayoutViewElement) {
      return new String[]{"include", "requestFocus"};
    }
    if (element instanceof LayoutElement) {
      return new String[]{"requestFocus"};
    }
    if (element instanceof Group || element instanceof StringArray || element instanceof IntegerArray || element instanceof Style) {
      return new String[]{"item"};
    }
    if (element instanceof Item) {
      return new String[]{"menu"};
    }
    if (element instanceof Menu) {
      return new String[]{"item", "group"};
    }
    if (element instanceof Attr) {
      return new String[]{"enum", "flag"};
    }
    if (element instanceof DeclareStyleable) {
      return new String[]{"attr"};
    }
    if (element instanceof Resources) {
      return new String[]{"string", "drawable", "dimen", "color", "style", "string-array", "integer-array", "array", "declare-styleable",
        "integer", "bool", "attr", "item", "eat-comment"};
    }
    if (element instanceof StyledText) {
      return new String[]{"b", "i", "u"};
    }
    if (element instanceof PreferenceElement) {
      return new String[]{"intent"};
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public static AttributeDefinition getAttributeDefinition(@NotNull AndroidFacet facet, @NotNull XmlAttribute attribute) {
    String localName = attribute.getLocalName();
    ResourceManager manager =
      facet.getResourceManager(attribute.getNamespace().equals(SdkConstants.NS_RESOURCES) ? SYSTEM_RESOURCE_PACKAGE : null);
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        return attrDefs.getAttrDefByName(localName);
      }
    }
    return null;
  }
}
