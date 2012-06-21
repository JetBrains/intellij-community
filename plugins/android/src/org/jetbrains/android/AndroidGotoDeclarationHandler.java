/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor) {
    if (!(sourceElement instanceof PsiIdentifier)) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(sourceElement);
    if (facet == null) {
      return null;
    }

    final PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(sourceElement, PsiReferenceExpression.class);
    if (refExp == null) {
      return null;
    }

    Pair<String, String> pair = AndroidResourceUtil.getReferredResourceField(facet, refExp);
    if (pair == null) {
      PsiElement parent = refExp.getParent();
      if (parent instanceof PsiReferenceExpression) {
        pair = AndroidResourceUtil.getReferredResourceField(facet, (PsiReferenceExpression)parent);
      }
      if (pair == null) {
        parent = parent.getParent();
        if (parent instanceof PsiReferenceExpression) {
          pair = AndroidResourceUtil.getReferredResourceField(facet, (PsiReferenceExpression)parent);
        }
      }
    }
    if (pair == null) {
      return null;
    }
    final String resClassName = pair.getFirst();
    final String resFieldName = pair.getSecond();

    final List<PsiElement> resourceList = facet.getLocalResourceManager().findResourcesByFieldName(resClassName, resFieldName);
    final PsiElement[] resources = resourceList.toArray(new PsiElement[resourceList.size()]);
    final PsiElement[] wrappedResources = new PsiElement[resources.length];

    for (int i = 0; i < resources.length; i++) {
      final PsiElement resource = resources[i];

      if (resource instanceof XmlAttributeValue &&
          resource instanceof PsiMetaOwner &&
          resource instanceof NavigationItem) {
        wrappedResources[i] = new ValueResourceElementWrapper((XmlAttributeValue)resource);
      }
      else if (resource instanceof PsiFile) {
        wrappedResources[i] = new FileResourceElementWrapper((PsiFile)resource);
      }
      else {
        wrappedResources[i] = resource;
      }
    }
    return wrappedResources.length > 0 ? wrappedResources : null;
  }

  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}
