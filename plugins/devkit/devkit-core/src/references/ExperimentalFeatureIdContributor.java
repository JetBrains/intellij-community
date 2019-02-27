// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.ExperimentalFeatureImpl;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.impl.ExtensionDomExtender;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;
import org.jetbrains.uast.UExpression;

import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.string;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;

class ExperimentalFeatureIdContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar
      .registerUastReferenceProvider(registrar,
                                     injectionHostUExpression().methodCallParameter(0, psiMethod()
                                       .withName(string().oneOf("isFeatureEnabled", "setFeatureEnabled"))
                                       .definedInClass(Experiments.class.getName())),
                                     new UastInjectionHostReferenceProvider() {
                                       @NotNull
                                       @Override
                                       public PsiReference[] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                                           @NotNull PsiLanguageInjectionHost host,
                                                                                           @NotNull ProcessingContext context) {
                                         return new PsiReference[]{new ExperimentalFeatureIdReference(host)};
                                       }
                                     }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }


  private static class ExperimentalFeatureIdReference extends ExtensionPointReferenceBase {

    private ExperimentalFeatureIdReference(PsiElement element) {
      super(element);
    }

    @Override
    protected String getExtensionPointClassname() {
      return ExperimentalFeatureImpl.class.getName();
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Cannot resolve feature '" + getValue() + "'";
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final List<LookupElement> variants = new SmartList<>();
      processCandidates(extension -> {
        final GenericAttributeValue<String> id = extension.getId();
        if (id == null || extension.getXmlElement() == null) return true;

        final String value = id.getStringValue();
        if (value == null) return true;

        final boolean requireRestart = "true".equals(getAttributeValue(extension, "requireRestart"));
        final boolean isInternalFeature = "true".equals(getAttributeValue(extension, "internalFeature"));
        final String description = " " + StringUtil.notNullize(getDescription(extension), "No Description");
        final String percentage = getAttributeValue(extension, "percentOfUsers");

        variants.add(LookupElementBuilder.create(extension.getXmlElement(), value)
                       .withIcon(requireRestart ? AllIcons.Nodes.PluginRestart : AllIcons.Nodes.Plugin)
                       .withBoldness(isInternalFeature)
                       .withTailText(description, true)
                       .withTypeText(percentage != null ? percentage + "%" : ""));
        return true;
      });
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }

    @Nullable
    private static String getDescription(Extension extension) {
      final DomFixedChildDescription description = extension.getGenericInfo().getFixedChildDescription("description");
      if (description == null) return null;
      final DomElement element = ContainerUtil.getFirstItem(description.getValues(extension));
      if (element instanceof ExtensionDomExtender.SimpleTagValue) {
        return ((ExtensionDomExtender.SimpleTagValue)element).getTagValue();
      }
      return null;
    }
  }
}
