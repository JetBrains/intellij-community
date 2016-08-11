/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 10/7/11
 */
public class PropertyKeyReferenceProvider extends PsiReferenceProvider {

  private final boolean myTagMode;
  private final String myFallbackKeyName;
  private final String myFallbackGroupName;

  public PropertyKeyReferenceProvider(boolean tagMode, String fallbackKeyName, String fallbackGroupName) {
    myTagMode = tagMode;
    myFallbackKeyName = fallbackKeyName;
    myFallbackGroupName = fallbackGroupName;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof IProperty;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (myTagMode && element instanceof XmlTag) {
      return getTagReferences(((XmlTag)element));
    }

    if (element instanceof XmlAttributeValue) {
      final XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();
      if (element.getTextLength() < 2) {
        return PsiReference.EMPTY_ARRAY;
      }

      final XmlTag tag = xmlAttribute.getParent();
      String value = null;
      String bundle = tag.getAttributeValue("bundle");
      if ("key".equals(xmlAttribute.getName())) {
        value = xmlAttribute.getValue();
      }
      else if (myFallbackKeyName.equals(xmlAttribute.getName())) {
        value = xmlAttribute.getValue();
        final String groupBundle = tag.getAttributeValue(myFallbackGroupName);
        if (groupBundle != null) {
          bundle = groupBundle;
        }
      }

      if (value != null) {
        return new PsiReference[]{new MyPropertyReference(value, xmlAttribute.getValueElement(), bundle)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private PsiReference[] getTagReferences(XmlTag element) {
    final XmlTag parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (parent == null) return PsiReference.EMPTY_ARRAY;
    final XmlTag groupNameTag = parent.findFirstSubTag(myFallbackGroupName);
    String bundleName = groupNameTag != null ? groupNameTag.getValue().getTrimmedText() : null;
    return new PsiReference[]{new MyPropertyReference(element.getValue().getText(), element, bundleName)};
  }


  private static class MyPropertyReference extends PropertyReference {

    @Nullable
    private final String myBundleName;

    private MyPropertyReference(String value, PsiElement psiElement, @Nullable String bundleName) {
      super(value, psiElement, bundleName, false);
      myBundleName = bundleName;
    }

    @Nullable
    @Override
    protected List<PropertiesFile> getPropertiesFiles() {
      return retrievePropertyFilesByBundleName(myBundleName, getElement());
    }

    @Override
    protected List<PropertiesFile> retrievePropertyFilesByBundleName(String bundleName, PsiElement element) {
      final List<String> allBundleNames;
      if (bundleName == null) {
        allBundleNames = getPluginResourceBundles(element);
      }
      else {
        allBundleNames = Collections.singletonList(bundleName);
      }

      final Project project = element.getProject();
      final PropertiesReferenceManager propertiesReferenceManager = PropertiesReferenceManager.getInstance(project);
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);

      final List<PropertiesFile> allPropertiesFiles = new ArrayList<>();
      for (String name : allBundleNames) {
        final List<PropertiesFile> propertiesFiles = propertiesReferenceManager
          .findPropertiesFiles(searchScope, name, BundleNameEvaluator.DEFAULT);
        allPropertiesFiles.addAll(propertiesFiles);
      }
      return allPropertiesFiles;
    }

    private static List<String> getPluginResourceBundles(PsiElement element) {
      final DomElement domElement = DomUtil.getDomElement(element);
      if (domElement == null) return Collections.emptyList();
      final DomElement rootElement = DomUtil.getFileElement(domElement).getRootElement();
      if (!(rootElement instanceof IdeaPlugin)) return Collections.emptyList();

      IdeaPlugin plugin = (IdeaPlugin)rootElement;
      return ContainerUtil.map(plugin.getResourceBundles(), value -> value.getStringValue());
    }
  }
}