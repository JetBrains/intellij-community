// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.IconDescriptionBundleEP;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

public class I18nReferenceContributor extends PsiReferenceContributor {

  private static final String INTENTION_ACTION_TAG = "intentionAction";
  private static final String INTENTION_ACTION_BUNDLE_TAG = "bundleName";

  private static final String SEPARATOR_TAG = "separator";

  private static class Holder {
    private static final String CONFIGURABLE_EP = ConfigurableEP.class.getName();
    private static final String INSPECTION_EP = InspectionEP.class.getName();

    private static final String ICON_DESCRIPTION_BUNDLE_EP = IconDescriptionBundleEP.class.getName();
    private static final String TYPE_NAME_EP = TypeNameEP.class.getName();
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerKeyProviders(registrar);

    registerBundleNameProviders(registrar);
  }

  private static void registerKeyProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"key", "groupKey"},
                                                                  Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP),
                                        new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceKey"},
                                                                  Holder.TYPE_NAME_EP),
                                        new PropertyKeyReferenceProvider(false, "resourceKey", "resourceBundle"));

    final XmlAttributeValuePattern separatorKeyPattern =
      xmlAttributeValue("key")
        .withSuperParent(2, DomPatterns.tagWithDom(SEPARATOR_TAG, Separator.class));
    registrar.registerReferenceProvider(separatorKeyPattern,
                                        new PropertyKeyReferenceProvider(tag -> {
                                          final DomElement domElement = DomUtil.getDomElement(tag);
                                          if (domElement == null) return null;

                                          final Actions actions = DomUtil.getParentOfType(domElement, Actions.class, true);
                                          return actions != null ? actions.getResourceBundle().getStringValue() : null;
                                        }));

    final XmlTagPattern.Capture intentionActionKeyTagPattern =
      xmlTag().withLocalName("categoryKey").
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
    registrar.registerReferenceProvider(intentionActionKeyTagPattern,
                                        new PropertyKeyReferenceProvider(true, null, INTENTION_ACTION_BUNDLE_TAG));
  }

  private static void registerBundleNameProviders(PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider bundleReferenceProvider = new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[]{new MyResourceBundleReference(element)};
      }
    };

    final XmlTagPattern.Capture resourceBundleTagPattern =
      xmlTag().withLocalName("resource-bundle").
        withParent(DomPatterns.tagWithDom(IdeaPlugin.TAG_NAME, IdeaPlugin.class));
    registrar.registerReferenceProvider(resourceBundleTagPattern, bundleReferenceProvider);

    final XmlAttributeValuePattern actionsResourceBundlePattern =
      xmlAttributeValue("resource-bundle")
        .withSuperParent(2, DomPatterns.tagWithDom("actions", Actions.class));
    registrar.registerReferenceProvider(actionsResourceBundlePattern, bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"bundle"}, "groupBundle",
                                                                  Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceBundle"},
                                                                  Holder.TYPE_NAME_EP, Holder.ICON_DESCRIPTION_BUNDLE_EP),
                                        bundleReferenceProvider);

    final XmlTagPattern.Capture intentionActionBundleTagPattern =
      xmlTag().withLocalName(INTENTION_ACTION_BUNDLE_TAG).
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
    registrar.registerReferenceProvider(intentionActionBundleTagPattern, bundleReferenceProvider);
  }

  private static XmlAttributeValuePattern extensionAttributePattern(String[] attributeNames,
                                                                    String... extensionPointClassNames) {
    //noinspection deprecation
    return xmlAttributeValue(attributeNames)
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

  private static final class MyResourceBundleReference extends ResourceBundleReference implements EmptyResolveMessageProvider {

    private MyResourceBundleReference(PsiElement element) {
      super(element, false);
    }

    @Override
    public Object @NotNull [] getVariants() {
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