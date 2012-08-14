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
package org.jetbrains.android.dom.converters;

import com.android.resources.ResourceType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.AdditionalConverter;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.android.resourceManagers.FileResourceProcessor;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author yole
 */
public class ResourceReferenceConverter extends ResolvingConverter<ResourceValue> implements CustomReferenceConverter<ResourceValue> {
  private final List<String> myResourceTypes;
  private ResolvingConverter<String> myAdditionalConverter;
  private boolean myAdditionalConverterSoft = false;
  private boolean myWithPrefix = true;
  private boolean myWithExplicitResourceType = true;
  private boolean myQuiet = false;

  public ResourceReferenceConverter() {
    this(new ArrayList<String>());
  }

  public ResourceReferenceConverter(@NotNull Collection<String> resourceTypes) {
    myResourceTypes = new ArrayList<String>(resourceTypes);
  }

  public ResourceReferenceConverter(@NotNull String resourceType, boolean withPrefix, boolean withExplicitResourceType) {
    myResourceTypes = Arrays.asList(resourceType);
    myWithPrefix = withPrefix;
    myWithExplicitResourceType = withExplicitResourceType;
  }

  public void setAdditionalConverter(ResolvingConverter<String> additionalConverter, boolean soft) {
    myAdditionalConverter = additionalConverter;
    myAdditionalConverterSoft = soft;
  }

  public void setQuiet(boolean quiet) {
    myQuiet = quiet;
  }

  @NotNull
  private String getPackagePrefix(@Nullable String resourcePackage) {
    String prefix = myWithPrefix ? "@" : "";
    if (resourcePackage == null) return prefix;
    return prefix + resourcePackage + ':';
  }

  @Nullable
  private static String getValue(XmlElement element) {
    if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValue();
    }
    else if (element instanceof XmlTag) {
      return ((XmlTag)element).getValue().getText();
    }
    return null;
  }

  @NotNull
  public Collection<? extends ResourceValue> getVariants(ConvertContext context) {
    Set<ResourceValue> result = new HashSet<ResourceValue>();
    Module module = context.getModule();
    if (module == null) return result;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) return result;

    final Set<String> recommendedTypes = getResourceTypes(context);

    // hack to check if it is a real id attribute
    if (recommendedTypes.contains(ResourceType.ID.getName()) && recommendedTypes.size() == 1) {
      result.add(ResourceValue.reference(AndroidResourceUtil.NEW_ID_PREFIX));
    }

    XmlElement element = context.getXmlElement();
    if (element == null) return result;
    String value = getValue(element);
    assert value != null;

    if (!myQuiet || StringUtil.startsWithChar(value, '@')) {
      String resourcePackage = null;
      String systemPrefix = getPackagePrefix(SYSTEM_RESOURCE_PACKAGE);
      if (value.startsWith(systemPrefix)) {
        resourcePackage = SYSTEM_RESOURCE_PACKAGE;
      }
      else {
        result.add(ResourceValue.literal(systemPrefix));
      }
      if (recommendedTypes.size() == 1) {
        String type = recommendedTypes.iterator().next();
        boolean explicitResourceType = value.startsWith(getTypePrefix(resourcePackage, type)) || myWithExplicitResourceType;
        addResourceReferenceValues(facet, type, resourcePackage, result, explicitResourceType);
      }
      else {
        final Set<String> filteringSet = SYSTEM_RESOURCE_PACKAGE.equals(resourcePackage)
                                         ? null
                                         : getResourceTypesInCurrentModule(facet);

        for (ResourceType resourceType : ResourceType.values()) {
          final String type = resourceType.getName();
          String typePrefix = getTypePrefix(resourcePackage, type);
          if (value.startsWith(typePrefix)) {
            addResourceReferenceValues(facet, type, resourcePackage, result, true);
          }
          else if (recommendedTypes.contains(type) &&
                   (filteringSet == null || filteringSet.contains(type))) {
            result.add(ResourceValue.literal(typePrefix));
          }
        }
      }
    }
    final ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

    if (additionalConverter != null) {
      for (String variant : additionalConverter.getVariants(context)) {
        result.add(ResourceValue.literal(variant));
      }
    }
    return result;
  }

  @NotNull
  public static Set<String> getResourceTypesInCurrentModule(@NotNull AndroidFacet facet) {
    final Set<String> result = new HashSet<String>();
    final LocalResourceManager manager = facet.getLocalResourceManager();

    manager.processFileResources(null, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
        if (ResourceType.getEnum(resFolderType) != null) {
          result.add(resFolderType);
        }
        return true;
      }
    });

    result.addAll(manager.getValueResourceTypes());
    if (manager.getIds().size() > 0) {
      result.add(ResourceType.ID.getName());
    }
    return result;
  }

  @NotNull
  private String getTypePrefix(String resourcePackage, String type) {
    String typePart = type + '/';
    return getPackagePrefix(resourcePackage) + typePart;
  }

  private Set<String> getResourceTypes(ConvertContext context) {
    return getResourceTypes(context.getInvocationElement());
  }

  @NotNull
  public Set<String> getResourceTypes(@NotNull DomElement element) {
    AndroidResourceType resourceType = element.getAnnotation(AndroidResourceType.class);
    Set<String> types = new HashSet<String>(myResourceTypes);
    if (resourceType != null) {
      String s = resourceType.value();
      if (s != null) types.add(s);
    }
    if (types.size() == 0) {
      types.addAll(AndroidResourceUtil.getNames(AndroidResourceUtil.VALUE_RESOURCE_TYPES));
    }
    return types;
  }

  private void addResourceReferenceValues(AndroidFacet facet,
                                          String type,
                                          String resPackage,
                                          Collection<ResourceValue> result,
                                          boolean explicitResourceType) {
    final ResourceManager manager = facet.getResourceManager(resPackage);
    if (manager != null) {
      for (String name : manager.getResourceNames(type)) {
        result.add(referenceTo(type, resPackage, name, explicitResourceType));
      }
    }
  }

  private ResourceValue referenceTo(String type, String resPackage, String name, boolean explicitResourceType) {
    return ResourceValue.referenceTo(myWithPrefix ? '@' : 0, resPackage, explicitResourceType ? type : null, name);
  }

  public ResourceValue fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    ResourceValue parsed = ResourceValue.parse(s, true, myWithPrefix);
    final ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

    if ((parsed == null || !parsed.isReference()) && additionalConverter != null) {
      String value = additionalConverter.fromString(s, context);
      if (value != null) {
        return ResourceValue.literal(value);
      }
      else if (!myAdditionalConverterSoft) {
        return null;
      }
    }
    if (parsed != null && parsed.getResourceType() == null && myResourceTypes.size() == 1) {
      parsed.setResourceType(myResourceTypes.get(0));
    }
    return parsed;
  }

  @Nullable
  private ResolvingConverter<String> getAdditionalConverter(ConvertContext context) {
    if (myAdditionalConverter != null) {
      return myAdditionalConverter;
    }

    final AdditionalConverter additionalConverterAnnotation =
      context.getInvocationElement().getAnnotation(AdditionalConverter.class);

    if (additionalConverterAnnotation != null) {
      final Class<? extends ResolvingConverter> converterClass = additionalConverterAnnotation.value();

      if (converterClass != null) {
        final ConverterManager converterManager = ServiceManager.getService(ConverterManager.class);
        //noinspection unchecked
        return (ResolvingConverter<String>)converterManager.getConverterInstance(converterClass);
      }
    }
    return null;
  }

  public String toString(@Nullable ResourceValue element, ConvertContext context) {
    if (element == null) {
      return null;
    }
    if (myWithExplicitResourceType || !element.isReference()) {
      return element.toString();
    }
    return ResourceValue.referenceTo(element.getPrefix(), element.getPackage(), null,
                                     element.getResourceName()).toString();
  }

  @Override
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet != null) {
      final DomElement domElement = context.getInvocationElement();

      if (domElement instanceof GenericDomValue) {
        final String value = ((GenericDomValue)domElement).getStringValue();

        if (value != null) {
          ResourceValue resourceValue = ResourceValue.parse(value, false, myWithPrefix);
          if (resourceValue != null) {
            String aPackage = resourceValue.getPackage();
            String resTypeName = resourceValue.getResourceType();
            if (resTypeName == null && myResourceTypes.size() == 1) {
              resTypeName = myResourceTypes.get(0);
            }
            final String resourceName = resourceValue.getResourceName();
            final ResourceType resType = resTypeName != null ? ResourceType.getEnum(resTypeName) : null;

            if (aPackage == null &&
                resType != null &&
                resourceName != null &&
                AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
              final List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();

              if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resType)) {
                fixes.add(new CreateValueResourceQuickFix(facet, resType, resourceName, context.getFile(), false));
              }
              if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resType)) {
                fixes.add(new CreateFileResourceQuickFix(facet, resType, resourceName, context.getFile(), false));
              }
              return fixes.toArray(new LocalQuickFix[fixes.size()]);
            }
          }
        }
      }
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<ResourceValue> value, PsiElement element, ConvertContext context) {
    if ("@null".equals(value.getStringValue())) {
      return PsiReference.EMPTY_ARRAY;
    }

    Module module = context.getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        ResourceValue resValue = value.getValue();
        if (resValue != null && resValue.isReference()) {
          String resType = resValue.getResourceType();
          if (resType == null) return PsiReference.EMPTY_ARRAY;
          if (resValue.getPackage() == null && "+id".equals(resValue.getResourceType())) {
            return PsiReference.EMPTY_ARRAY;
          }
          return new PsiReference[]{new AndroidResourceReference(value, facet, resValue, null)};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
