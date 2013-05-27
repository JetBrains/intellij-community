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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.Iconable;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 10/7/11
 */
public class InspectionsPropertiesReferenceProviderContributor extends PsiReferenceContributor {

  private static final String[] EXTENSION_TAG_NAMES = new String[]{
    "localInspection", "globalInspection",
    "configurable", "applicationConfigurable", "projectConfigurable"
  };

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    ElementPattern pattern = createPattern("key", "groupKey");
    registrar.registerReferenceProvider(pattern, new InspectionsKeyPropertiesReferenceProvider(false),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);

    ElementPattern bundlePattern = createPattern("bundle", "groupBundle");
    registrar.registerReferenceProvider(bundlePattern, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        ResourceBundleReference reference = new ResourceBundleReference(element, false) {
          @NotNull
          @Override
          public Object[] getVariants() {
            PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(myElement.getProject());
            final List<LookupElement> variants = new ArrayList<LookupElement>();
            referenceManager.processPropertiesFiles(GlobalSearchScope.projectScope(myElement.getProject()), new PropertiesFileProcessor() {
              public boolean process(String baseName, PropertiesFile propertiesFile) {
                variants.add(LookupElementBuilder.create(propertiesFile, baseName)
                               .withIcon(propertiesFile.getContainingFile().getIcon(Iconable.ICON_FLAG_READ_STATUS)));
                return true;
              }
            }, this);
            return variants.toArray(new LookupElement[variants.size()]);
          }
        };
        return new PsiReference[]{reference};
      }
    }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  private static ElementPattern createPattern(String... attributeNames) {
    return XmlPatterns.xmlAttributeValue()
      .withParent(XmlPatterns.xmlAttribute().withLocalName(attributeNames)
                    .withParent(XmlPatterns.xmlTag().withName(EXTENSION_TAG_NAMES)
                                  .withSuperParent(2, XmlPatterns.xmlTag().withName("idea-plugin"))));
  }
}