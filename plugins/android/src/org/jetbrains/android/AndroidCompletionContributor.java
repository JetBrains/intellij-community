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

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animation.AnimationDomFileDescription;
import org.jetbrains.android.dom.color.ColorDomFileDescription;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.drawable.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * @author coyote
 */
public class AndroidCompletionContributor extends CompletionContributor {

  private static void addAll(Collection<String> collection, CompletionResultSet set) {
    for (String s : collection) {
      set.addElement(LookupElementBuilder.create(s));
    }
  }

  private static boolean complete(@NotNull AndroidFacet facet, PsiElement position, CompletionResultSet resultSet) {
    PsiElement parent = position.getParent();
    if (parent instanceof XmlTag) {
      XmlTag tag = (XmlTag)parent;
      if (tag.getParentTag() == null) {
        PsiFile file = tag.getContainingFile().getOriginalFile();
        if (file instanceof XmlFile) {
          XmlFile xmlFile = (XmlFile)file;
          if (ManifestDomFileDescription.isManifestFile(xmlFile)) {
            resultSet.addElement(LookupElementBuilder.create("manifest"));
            return false;
          }
          else if (LayoutDomFileDescription.isLayoutFile(xmlFile)) {
            resultSet.addElement(LookupElementBuilder.create("view"));
            resultSet.addElement(LookupElementBuilder.create("merge"));
            Map<String, PsiClass> viewClassMap = AndroidDomExtender.getViewClassMap(facet);
            for (String tagName : viewClassMap.keySet()) {
              final PsiClass viewClass = viewClassMap.get(tagName);
              if (!AndroidUtils.isAbstract(viewClass)) {
                resultSet.addElement(LookupElementBuilder.create(tagName));
              }
            }
            return false;
          }
          else if (AnimationDomFileDescription.isAnimationFile(xmlFile)) {
            addAll(AndroidAnimationUtils.getPossibleChildren(facet), resultSet);
            return false;
          }
          else if (XmlResourceDomFileDescription.isXmlResourceFile(xmlFile)) {
            addAll(AndroidXmlResourcesUtil.getPossibleRoots(facet), resultSet);
            return false;
          }
          else if (AndroidDrawableDomUtil.isDrawableResourceFile(xmlFile)) {
            addAll(AndroidDrawableDomUtil.getPossibleRoots(), resultSet);
          }
          else if (ColorDomFileDescription.isColorResourceFile(xmlFile)) {
            addAll(Arrays.asList(DrawableStateListDomFileDescription.SELECTOR_TAG_NAME), resultSet);
          }
        }
      }
    }
    return true;
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet resultSet) {
    PsiElement position = parameters.getPosition();
    AndroidFacet facet = AndroidFacet.getInstance(position);
    if (facet == null) return;
    if (!complete(facet, position, resultSet)) {
      resultSet.stopHere();
    }
  }
}
