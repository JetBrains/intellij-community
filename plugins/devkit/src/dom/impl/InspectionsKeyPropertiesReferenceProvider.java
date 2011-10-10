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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 * Date: 10/7/11
 */
public class InspectionsKeyPropertiesReferenceProvider extends PsiReferenceProvider {

  private final boolean myDefaultSoft;

  public InspectionsKeyPropertiesReferenceProvider() {
    this(false);
  }

  public InspectionsKeyPropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof IProperty;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    boolean soft = myDefaultSoft;

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
      else if ("groupKey".equals(xmlAttribute.getName())) {
        value = xmlAttribute.getValue();
        final String groupBundle = tag.getAttributeValue("groupBundle");
        if (groupBundle != null) {
          bundle = groupBundle;
        }
      }
      if (value != null) {
        return new PsiReference[]{new PropertyReference(value, xmlAttribute.getValueElement(), bundle, soft){
          @Override
          protected List<PropertiesFile> retrievePropertyFilesByBundleName(String bundleName, PsiElement element) {
            final Project project = element.getProject();
            return PropertiesReferenceManager.getInstance(project)
                     .findPropertiesFiles(GlobalSearchScope.projectScope(project), bundleName, BundleNameEvaluator.DEFAULT);
          }
        }};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}