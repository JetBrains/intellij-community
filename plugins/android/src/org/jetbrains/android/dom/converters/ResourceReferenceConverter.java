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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.AdditionalConverter;
import org.jetbrains.android.dom.ResourceType;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidBundle;
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
  private static final Set<String> FIXABLE_RESOURCE_TYPES =
    new HashSet<String>(Arrays.asList("anim", "layout", "style", "menu", "xml", "dimen", "color", "string", "array", "id", "drawable"));

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
    if (recommendedTypes.contains("id") && recommendedTypes.size() == 1) {
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

        for (String type : AndroidResourceUtil.REFERABLE_RESOURCE_TYPES) {
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
  private static Set<String> getResourceTypesInCurrentModule(@NotNull AndroidFacet facet) {
    final Set<String> result = new HashSet<String>();
    final LocalResourceManager manager = facet.getLocalResourceManager();
    
    for (VirtualFile resSubdir : manager.getResourceSubdirs(null)) {
      final String resType = AndroidResourceUtil.getResourceTypeByDirName(resSubdir.getName());
      
      if (resType != null && com.android.resources.ResourceType.getEnum(resType) != null) {
        result.add(resType);
      }
    }

    result.addAll(manager.getValueResourceTypes());
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
    ResourceType resourceType = element.getAnnotation(ResourceType.class);
    Set<String> types = new HashSet<String>(myResourceTypes);
    if (resourceType != null) {
      String s = resourceType.value();
      if (s != null) types.add(s);
    }
    if (types.size() == 0) {
      types.addAll(AndroidResourceUtil.REFERABLE_RESOURCE_TYPES);
    }
    return types;
  }

  private void addResourceReferenceValues(AndroidFacet facet,
                                          String type,
                                          String resPackage,
                                          Collection<ResourceValue> result,
                                          boolean explicitResourceType) {
    ResourceManager manager = facet.getResourceManager(resPackage);
    if (manager == null) return;
    for (String name : manager.getValueResourceNames(type)) {
      result.add(referenceTo(type, resPackage, name, explicitResourceType));
    }
    for (String file : manager.getFileResourcesNames(type)) {
      result.add(referenceTo(type, resPackage, file, explicitResourceType));
    }
    if (type.equals("id")) {
      for (String id : manager.getIds()) {
        result.add(referenceTo(type, resPackage, id, explicitResourceType));
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

  public String toString(@Nullable ResourceValue resourceElement, ConvertContext context) {
    return resourceElement != null ? resourceElement.toString() : null;
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
            String resourceType = resourceValue.getResourceType();
            if (resourceType == null && myResourceTypes.size() == 1) {
              resourceType = myResourceTypes.get(0);
            }
            final String resourceName = resourceValue.getResourceName();
            if (aPackage == null && resourceType != null && resourceName != null) {
              if (FIXABLE_RESOURCE_TYPES.contains(resourceType) && AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
                return new LocalQuickFix[]{new MyLocalQuickFix(facet, resourceType, resourceName, context.getFile())};
              }
            }
          }
        }
      }
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<ResourceValue> value, PsiElement element, ConvertContext context) {
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
          return new PsiReference[] {new AndroidResourceReference(value, facet, resValue)};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static class MyLocalQuickFix implements LocalQuickFix {
    private final AndroidFacet myFacet;
    private final String myResourceType;
    private final String myResourceName;
    private final PsiFile myFile;

    public MyLocalQuickFix(@NotNull AndroidFacet facet, @NotNull String resourceType, @NotNull String resourceName, @NotNull PsiFile file) {
      myFacet = facet;
      myResourceType = resourceType;
      myResourceName = resourceName;
      myFile = file;
    }

    @NotNull
    public String getName() {
      String containerName;
      if (ArrayUtil.find(AndroidResourceUtil.VALUE_RESOURCE_TYPES, myResourceType) >= 0) {
        containerName = AndroidResourceUtil.getDefaultResourceFileName(myResourceType);
      }
      else {
        containerName = '"' + myResourceType + "\" directory";
      }
      return AndroidBundle.message("create.resource.quickfix.name", myResourceName, containerName);
    }

    @NotNull
    public String getFamilyName() {
      return AndroidBundle.message("quick.fixes.family");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      LocalResourceManager manager = myFacet.getLocalResourceManager();
      if (ArrayUtil.find(AndroidResourceUtil.VALUE_RESOURCE_TYPES, myResourceType) >= 0) {
        String initialValue = !myResourceType.equals("id") ? "value" : null;
        ResourceElement resElement = manager.addValueResource(myResourceType, myResourceName, initialValue);
        if (resElement != null) {
          if (!(resElement instanceof Item)) {
            // then it is ID
            List<ResourceElement> list = manager.findValueResources(myResourceType, myResourceName);
            if (list.size() == 1) {
              ResourceElement element = list.get(0);
              XmlTag tag = element.getXmlTag();
              PsiNavigateUtil.navigate(tag.getValue().getTextElements()[0]);
              tag.getValue().setText("");
            }
          }
        }
      }
      else {
        manager.addResourceFileAndNavigate(myResourceName, myResourceType);
      }
      UndoUtil.markPsiFileForUndo(myFile);
    }
  }
}
