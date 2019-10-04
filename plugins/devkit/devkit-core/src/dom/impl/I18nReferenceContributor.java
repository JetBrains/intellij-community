/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.ide.TypeNameEP;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.XmlPatterns.xmlTag;

public class I18nReferenceContributor extends PsiReferenceContributor {

  private static final String INTENTION_ACTION_TAG = "intentionAction";
  private static final String INTENTION_ACTION_BUNDLE_TAG = "bundleName";

  private static final String CONFIGURABLE_EP = ConfigurableEP.class.getName();
  private static final String INSPECTION_EP = InspectionEP.class.getName();

  private static final String TYPE_NAME_EP = TypeNameEP.class.getName();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerKeyProviders(registrar);

    registerBundleNameProviders(registrar);
  }

  private static void registerKeyProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"key", "groupKey"},
                                                                  CONFIGURABLE_EP, INSPECTION_EP),
                                        new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceKey"},
                                                                  TYPE_NAME_EP),
                                        new PropertyKeyReferenceProvider(false, "resourceKey", "resourceBundle"));

    final XmlTagPattern.Capture intentionActionKeyTagPattern =
      xmlTag().withLocalName("categoryKey").
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
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
      xmlTag().withLocalName("resource-bundle").
        withParent(DomPatterns.tagWithDom(IdeaPlugin.TAG_NAME, IdeaPlugin.class));
    registrar.registerReferenceProvider(resourceBundleTagPattern, bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"bundle"}, "groupBundle",
                                                                  CONFIGURABLE_EP, INSPECTION_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceBundle"},
                                                                  TYPE_NAME_EP),
                                        bundleReferenceProvider);

    final XmlTagPattern.Capture intentionActionBundleTagPattern =
      xmlTag().withLocalName(INTENTION_ACTION_BUNDLE_TAG).
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
    registrar.registerReferenceProvider(intentionActionBundleTagPattern, bundleReferenceProvider);
  }

  private static XmlAttributeValuePattern extensionAttributePattern(String[] attributeNames,
                                                                    String... extensionPointClassNames) {
    //noinspection deprecation
    return XmlPatterns.xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(2, xmlTag()
        .and(DomPatterns.withDom(DomPatterns.domElement(Extension.class).with(new PatternCondition<Extension>("relevantEP") {
          @Override
          public boolean accepts(@NotNull Extension extension,
                                 ProcessingContext context) {
            final ExtensionPoint extensionPoint = extension.getExtensionPoint();
            assert extensionPoint != null;
            final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
            for (String name : extensionPointClassNames) {
              if (InheritanceUtil.isInheritor(beanClass, name)) return true;
            }
            return false;
          }
        }))));
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
      final List<LookupElement> variants = new ArrayList<>();
      referenceManager.processPropertiesFiles(GlobalSearchScopesCore.projectProductionScope(project), (baseName, propertiesFile) -> {
        final Icon icon = propertiesFile.getContainingFile().getIcon(Iconable.ICON_FLAG_READ_STATUS);
        final String relativePath = ProjectUtil.calcRelativeToProjectPath(propertiesFile.getVirtualFile(), project);
        variants.add(LookupElementBuilder.create(propertiesFile, baseName)
                       .withIcon(icon)
                       .withTailText(" (" + relativePath + ")", true));
        return true;
      }, this);
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Cannot resolve property bundle";
    }
  }
}