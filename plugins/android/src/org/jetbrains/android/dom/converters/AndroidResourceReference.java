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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.ConvertContextImpl;
import com.intellij.util.xml.impl.DomCompletionContributor;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.android.resourceManagers.ValueResourceInfoImpl;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author coyote
 */
public class AndroidResourceReference extends PsiReferenceBase.Poly<XmlElement> {
  private final GenericDomValue<ResourceValue> myValue;
  private final AndroidFacet myFacet;
  private final ResourceValue myResourceValue;

  public AndroidResourceReference(@NotNull GenericDomValue<ResourceValue> value,
                                  @NotNull AndroidFacet facet,
                                  @NotNull ResourceValue resourceValue) {
    super(DomUtil.getValueElement(value), null, true);
    myValue = value;
    myFacet = facet;
    myResourceValue = resourceValue;
  }

  @NotNull
  public Object[] getVariants() {
    final Converter converter = WrappingConverter.getDeepestConverter(myValue.getConverter(), myValue);
    if (converter instanceof EnumConverter || converter == AndroidDomUtil.BOOLEAN_CONVERTER) {
      if (DomCompletionContributor.isSchemaEnumerated(getElement())) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
      ArrayList<Object> result = new ArrayList<Object>();
      final ConvertContext convertContext = new ConvertContextImpl(myValue);
      for (Object variant : resolvingConverter.getVariants(convertContext)) {
        String name = converter.toString(variant, convertContext);
        if (name != null) {
          result.add(ElementPresentationManager.getInstance().createVariant(variant, name, resolvingConverter.getPsiElement(variant)));
        }
      }
      return result.toArray();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    System.out.println("Handle element rename to " + newElementName);
    if (newElementName.startsWith(AndroidResourceUtil.NEW_ID_PREFIX)) {
      newElementName = AndroidResourceUtil.getResourceNameByReferenceText(newElementName);
    }
    ResourceValue value = myValue.getValue();
    assert value != null;
    String resType = value.getResourceType();
    System.out.println("Restype: " + resType);

    if (resType != null && newElementName != null) {
      // todo: do not allow new value resource name to contain dot, because it is impossible to check if it file or value otherwise

      final String newResName = newElementName.contains(".") // it is file
                                ? AndroidCommonUtils.getResourceName(resType, newElementName)
                                : newElementName;
      System.out.println("New res name: " + newResName);
      myValue.setValue(ResourceValue.referenceTo(value.getPrefix(), value.getPackage(), resType, newResName));
    }
    return myValue.getXmlTag();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  public PsiElement[] computeTargetElements() {
    final ResolveResult[] resolveResults = multiResolve(false);
    final List<PsiElement> results = new ArrayList<PsiElement>();

    for (ResolveResult result : resolveResults) {
      PsiElement element = result.getElement();

      if (element instanceof LazyValueResourceElementWrapper) {
        element = ((LazyValueResourceElementWrapper)element).computeElement();
      }

      if (element instanceof ResourceElementWrapper) {
        element = ((ResourceElementWrapper)element).getWrappee();
      }

      if (element != null) {
        results.add(element);
      }
    }
    return results.toArray(new PsiElement[results.size()]);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(myElement.getProject())
      .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<AndroidResourceReference>() {
        @NotNull
        @Override
        public ResolveResult[] resolve(@NotNull AndroidResourceReference reference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, incompleteCode);
  }

  @NotNull
  private ResolveResult[] resolveInner() {
    final List<PsiElement> elements = new ArrayList<PsiElement>();
    final List<PsiFile> files = new ArrayList<PsiFile>();
    collectTargets(myFacet, myResourceValue, elements, files);

    final List<ResolveResult> result = new ArrayList<ResolveResult>();

    for (PsiFile target : files) {
      if (target != null) {
        final PsiFile e = new FileResourceElementWrapper(target);
        result.add(new PsiElementResolveResult(e));
      }
    }

    for (PsiElement target : elements) {
      result.add(new PsiElementResolveResult(target));
    }
    return result.toArray(new ResolveResult[result.size()]);
  }

  private void collectTargets(AndroidFacet facet, ResourceValue resValue, List<PsiElement> elements, List<PsiFile> files) {
    String resType = resValue.getResourceType();
    if (resType == null) {
      return;
    }
    ResourceManager manager = facet.getResourceManager(resValue.getPackage());
    if (manager != null) {
      String resName = resValue.getResourceName();
      if (resName != null) {
        List<ValueResourceInfoImpl> valueResources = manager.findValueResourceInfos(resType, resName, false);

        for (final ValueResourceInfo resource : valueResources) {
          elements.add(new LazyValueResourceElementWrapper(resource, myElement));
        }
        if (resType.equals("id")) {
          elements.addAll(manager.findIdDeclarations(resName));
        }
        if (elements.size() == 0) {
          files.addAll(manager.findResourceFiles(resType, resName, false));
        }
      }
    }
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    final ResolveResult[] results = multiResolve(false);
    final PsiFile psiFile = element.getContainingFile();
    final VirtualFile vFile = psiFile != null ? psiFile.getVirtualFile() : null;

    for (ResolveResult result : results) {
      final PsiElement target = result.getElement();

      if (element.getManager().areElementsEquivalent(target, element)) {
        return true;
      }

      if (target instanceof LazyValueResourceElementWrapper && vFile != null) {
        final ValueResourceInfo info = ((LazyValueResourceElementWrapper)target).getResourceInfo();

        if (info.getContainingFile().equals(vFile)) {
          final XmlAttributeValue realTarget = info.computeXmlElement();

          if (element.getManager().areElementsEquivalent(realTarget, element)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
