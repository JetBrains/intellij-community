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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.ConvertContextImpl;
import com.intellij.util.xml.impl.DomCompletionContributor;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author coyote
 */
public abstract class BaseResourceReference extends PsiReferenceBase.Poly<XmlElement> {
  private final GenericDomValue<ResourceValue> myValue;

  public BaseResourceReference(GenericDomValue<ResourceValue> value) {
    super(DomUtil.getValueElement(value), null, true);
    myValue = value;
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
    if (newElementName.startsWith(AndroidResourceUtil.NEW_ID_PREFIX)) {
      newElementName = AndroidResourceUtil.getResourceNameByReferenceText(newElementName);
    }
    ResourceValue value = myValue.getValue();
    assert value != null;
    String resType = value.getResourceType();
    if (resType != null && newElementName != null) {
      myValue.setValue(ResourceValue.referenceTo(value.getPrefix(), value.getPackage(), resType,
                                                 ResourceManager.getResourceName(resType, newElementName)));
    }
    return myValue.getXmlTag();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }
}
