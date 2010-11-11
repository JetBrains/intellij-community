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

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animation.AnimationDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

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

  private static boolean containsNamespace(@NotNull XmlTag tag, @NotNull String namespace) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      if ("xmlns:android".equals(attribute.getName()) && namespace.equals(attribute.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isXmlResource(@NotNull AndroidFacet facet, @NotNull VirtualFile file) {
    String extension = FileUtil.getExtension(file.getName());
    if (!extension.equals("xml")) return false;
    VirtualFile parent = file.getParent();
    if (parent == null) return false;
    parent = parent.getParent();
    return parent != null && facet.getLocalResourceManager().isResourceDir(parent);
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
            final PsiClass viewGroupClass = viewClassMap.get("ViewGroup");
            for (String tagName : viewClassMap.keySet()) {
              final PsiClass viewClass = viewClassMap.get(tagName);
              if (!AndroidUtils.isAbstract(viewClass)) {
                boolean inheritsViewGroup = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                  public Boolean compute() {
                    return viewClass.isInheritor(viewGroupClass, true);
                  }
                });
                if (inheritsViewGroup) {
                  resultSet.addElement(LookupElementBuilder.create(tagName));
                }
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
        }
      }
    }
    VirtualFile containingFile = parent.getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile != null &&
        (isXmlResource(facet, containingFile) || AndroidRootUtil.getManifestFile(facet.getModule()) == containingFile)) {
      if (parent instanceof XmlAttribute) {
        XmlAttribute attribute = (XmlAttribute)parent;
        XmlTag tag = attribute.getParent();
        if (!containsNamespace(tag, SdkConstants.NS_RESOURCES)) {
          String prefix = attribute.getNamespacePrefix();
          String s = null;
          if (prefix.length() == 0) {
            s = "xmlns:android";
          }
          else if (prefix.equals("xmlns")) {
            s = "android";
          }
          if (s != null) {
            resultSet.addElement(LookupElementBuilder.create(s));
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
