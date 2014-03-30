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

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 10/7/11
 */
public class I18nReferenceContributor extends PsiReferenceContributor {

  private static final String[] EXTENSION_TAG_NAMES = new String[]{
    "localInspection", "globalInspection",
    "configurable", "applicationConfigurable", "projectConfigurable"
  };

  private static final String[] TYPE_NAME_TAG = new String[]{"typeName"};
  private static final String INTENTION_ACTION_TAG = "intentionAction";
  private static final String INTENTION_ACTION_BUNDLE_TAG = "bundleName";

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registerKeyProviders(registrar);

    registerBundleNameProviders(registrar);
  }

  private static void registerKeyProviders(PsiReferenceRegistrar registrar) {
    ElementPattern pattern = createPattern(EXTENSION_TAG_NAMES, "key", "groupKey");
    registrar.registerReferenceProvider(pattern,
                                        new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle"),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);

    ElementPattern typeNameKeyPattern = createPattern(TYPE_NAME_TAG, "resourceKey");
    registrar.registerReferenceProvider(typeNameKeyPattern,
                                        new PropertyKeyReferenceProvider(false, "resourceKey", "resourceBundle"),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);

    final XmlTagPattern.Capture intentionActionKeyTagPattern =
      XmlPatterns.xmlTag().withName("categoryKey").
        withParent(XmlPatterns.xmlTag().withName(INTENTION_ACTION_TAG).
          withSuperParent(2, XmlPatterns.xmlTag().withName("idea-plugin")));
    registrar.registerReferenceProvider(intentionActionKeyTagPattern,
                                        new PropertyKeyReferenceProvider(true, null, INTENTION_ACTION_BUNDLE_TAG));
  }

  private static void registerBundleNameProviders(PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider bundleReferenceProvider = new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new MyResourceBundleReference(element)};
      }
    };

    final XmlTagPattern.Capture resourceBundleTagPattern =
      XmlPatterns.xmlTag().withName("resource-bundle").withParent(XmlPatterns.xmlTag().withName("idea-plugin"));
    registrar.registerReferenceProvider(resourceBundleTagPattern, bundleReferenceProvider);

    ElementPattern bundlePattern = createPattern(EXTENSION_TAG_NAMES, "bundle", "groupBundle");
    registrar.registerReferenceProvider(bundlePattern, bundleReferenceProvider,
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);

    ElementPattern typeNameBundlePattern = createPattern(TYPE_NAME_TAG, "resourceBundle");
    registrar.registerReferenceProvider(typeNameBundlePattern, bundleReferenceProvider,
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);

    final XmlTagPattern.Capture intentionActionBundleTagPattern =
      XmlPatterns.xmlTag().withName(INTENTION_ACTION_BUNDLE_TAG).
        withParent(XmlPatterns.xmlTag().withName(INTENTION_ACTION_TAG).
          withSuperParent(2, XmlPatterns.xmlTag().withName("idea-plugin")));
    registrar.registerReferenceProvider(intentionActionBundleTagPattern, bundleReferenceProvider,
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  private static ElementPattern createPattern(String[] tagNames, String... attributeNames) {
    return XmlPatterns.xmlAttributeValue()
      .withParent(XmlPatterns.xmlAttribute().withLocalName(attributeNames)
                    .withParent(XmlPatterns.xmlTag().withName(tagNames)
                                  .withSuperParent(2, XmlPatterns.xmlTag().withName("idea-plugin"))));
  }


  private static class MyResourceBundleReference extends ResourceBundleReference implements EmptyResolveMessageProvider {

    private MyResourceBundleReference(PsiElement element) {
      super(element, false);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final Project project = myElement.getProject();
      PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(project);
      final List<LookupElement> variants = new ArrayList<LookupElement>();
      referenceManager.processPropertiesFiles(GlobalSearchScopesCore.projectProductionScope(project), new PropertiesFileProcessor() {
        public boolean process(String baseName, PropertiesFile propertiesFile) {
          final Icon icon = propertiesFile.getContainingFile().getIcon(Iconable.ICON_FLAG_READ_STATUS);
          final String relativePath = ProjectUtil.calcRelativeToProjectPath(propertiesFile.getVirtualFile(), project);
          variants.add(LookupElementBuilder.create(propertiesFile, baseName)
                         .withIcon(icon)
                         .withTailText(" (" + relativePath + ")", true));
          return true;
        }
      }, this);
      return variants.toArray(new LookupElement[variants.size()]);
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Cannot resolve property bundle";
    }
  }
}