// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ExperimentalFeature;
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
import org.jetbrains.idea.devkit.util.ExtensionLocator;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;

class ExperimentalFeatureIdContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      psiLiteral()
        .methodCallParameter(0, psiMethod().withName("isFeatureEnabled", "setFeatureEnabled")
          .inClass(psiClass().withQualifiedName(Experiments.class.getName()))),
      new PsiReferenceProvider() {
        @NotNull
        @Override
        public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                     @NotNull ProcessingContext context) {
          return new PsiReference[]{new ExperimentalFeatureIdReference(element)};
        }
      }, PsiReferenceRegistrar.HIGHER_PRIORITY);
  }

  private static class ExperimentalFeatureIdReference extends PsiReferenceBase<PsiElement> {

    private ExperimentalFeatureIdReference(PsiElement element) {
      super(element);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      final String myId = getValue();
      final CommonProcessors.FindProcessor<Extension> resolveProcessor = new CommonProcessors.FindProcessor<Extension>() {
        @Override
        protected boolean accept(Extension extension) {
          return myId.equals(extension.getId().getStringValue());
        }
      };
      processCandidates(resolveProcessor);

      final Extension value = resolveProcessor.getFoundValue();
      if (value == null) {
        return null;
      }
      final DomTarget target = DomTarget.getTarget(value);
      return target != null ? PomService.convertToPsi(target) : value.getXmlElement();
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
      return variants.toArray(new LookupElement[variants.size()]);
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

    @Nullable
    private static String getAttributeValue(Extension extension, String attributeName) {
      final DomAttributeChildDescription attributeDescription = extension.getGenericInfo().getAttributeChildDescription(attributeName);
      if (attributeDescription == null) {
        return null;
      }

      return attributeDescription.getDomAttributeValue(extension).getStringValue();
    }

    private void processCandidates(Processor<Extension> processor) {
      final Project project = myElement.getProject();

      final PsiClass experimentalFeatureClass =
        JavaPsiFacade.getInstance(project).findClass(ExperimentalFeature.class.getName(), myElement.getResolveScope());
      if (experimentalFeatureClass == null) return;

      final ExtensionPointLocator extensionPointLocator = new ExtensionPointLocator(experimentalFeatureClass);
      final ExtensionPointCandidate extensionPointCandidate = ContainerUtil.getFirstItem(extensionPointLocator.findDirectCandidates());
      if (extensionPointCandidate == null) return;

      final DomManager manager = DomManager.getDomManager(project);
      final DomElement extensionPointDomElement = manager.getDomElement(extensionPointCandidate.pointer.getElement());
      if (!(extensionPointDomElement instanceof ExtensionPoint)) return;

      final ExtensionLocator locator = ExtensionLocator.byExtensionPoint((ExtensionPoint)extensionPointDomElement);
      for (ExtensionCandidate candidate : locator.findCandidates()) {
        final XmlTag element = candidate.pointer.getElement();
        final DomElement domElement = manager.getDomElement(element);
        if (domElement instanceof Extension) {
          if (!processor.process((Extension)domElement)) return;
        }
      }
    }
  }
}
