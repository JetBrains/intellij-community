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

package org.jetbrains.android;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 5, 2009
 * Time: 4:00:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element;
      if (AndroidResourceUtil.findIdFields(value).length > 0) {
        return true;
      }
    }
    element = correctResourceElement(element);
    if (element instanceof PsiField) {
      return AndroidResourceUtil.isResourceField((PsiField)element);
    }
    else if (element instanceof PsiFile) {
      return AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)element, true).length > 0;
    }
    else if (element instanceof XmlTag) {
      return AndroidResourceUtil.findResourceFieldsForValueResource((XmlTag)element, true).length > 0;
    }
    return false;
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    private final PsiElement[] myAdditionalElements;

    protected MyFindUsagesHandler(@NotNull PsiElement element, PsiElement... additionalElements) {
      super(element);
      myAdditionalElements = additionalElements;
    }

    @NotNull
    @Override
    public PsiElement[] getSecondaryElements() {
      return myAdditionalElements;
    }
  }

  @Nullable
  private static PsiElement correctResourceElement(PsiElement element) {
    if (element instanceof XmlElement && !(element instanceof XmlFile)) {
      XmlTag tag = element instanceof XmlTag ? (XmlTag)element : PsiTreeUtil.getParentOfType(element, XmlTag.class);
      DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
      if (domElement instanceof ResourceElement) {
        return tag;
      }
      return null;
    }
    return element;
  }

  private static XmlAttributeValue wrapIfNeccessary(XmlAttributeValue value) {
    if (value instanceof ResourceElementWrapper) {
      return value;
    }
    return new ValueResourceElementWrapper(value);
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    PsiElement e = element;
    AndroidFacet facet = AndroidFacet.getInstance(e);
    assert facet != null;
    if (e instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)e;
      PsiField[] fields = AndroidResourceUtil.findIdFields(value);
      if (fields.length > 0) {
        e = wrapIfNeccessary(value);
        return new MyFindUsagesHandler(e, fields);
      }
    }
    e = correctResourceElement(e);
    assert e != null;
    if (e instanceof PsiFile) {
      // resource file
      PsiField[] fields = AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)e, true);
      if (fields.length == 0) {
        return null;
      }
      return new MyFindUsagesHandler(e, fields);
    }
    else if (e instanceof XmlTag) {
      // value resource
      XmlTag tag = (XmlTag)e;
      PsiField[] fields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, true);
      if (fields.length == 0) {
        return null;
      }
      XmlAttributeValue nameValue = AndroidUtils.getNameAttrValue(tag);
      assert nameValue != null;
      return new MyFindUsagesHandler(nameValue, fields);
    }
    else if (e instanceof PsiField) {
      PsiField field = (PsiField)e;
      List<PsiElement> resources = AndroidResourceUtil.findResourcesByField(field);
      if (resources.size() == 0) {
        return new MyFindUsagesHandler(e);
      }

      // ignore alternative resources because their usages are the same
      PsiElement resource = resources.get(0);
      return createFindUsagesHandler(resource, forHighlightUsages);
    }
    return null;
  }

}
