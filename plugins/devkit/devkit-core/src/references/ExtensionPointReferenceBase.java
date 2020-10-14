// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;

import java.util.List;

abstract class ExtensionPointReferenceBase extends PsiReferenceBase<PsiElement> implements PluginConfigReference {

  protected ExtensionPointReferenceBase(@NotNull PsiElement element) {
    super(element);
  }

  protected ExtensionPointReferenceBase(@NotNull PsiElement element, TextRange rangeInElement) {
    super(element, rangeInElement);
  }

  /**
   * @see ExtensionPoint#getEffectiveQualifiedName()
   */
  protected abstract String getExtensionPointFqn();

  /**
   * Returns the name attribute for resolving.
   * Must return {@code true} from {@link #hasCustomNameElement()} when overriding.
   *
   * @see #getAttribute(Extension, String)
   */
  protected GenericAttributeValue<?> getNameElement(Extension extension) {
    return extension.getId();
  }

  /**
   * @see #getNameElement(Extension)
   */
  protected boolean hasCustomNameElement() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final String resolveId = getResolveValue();

    final CommonProcessors.FindProcessor<Extension> resolveProcessor;
    if (hasCustomNameElement()) {
      resolveProcessor = new CommonProcessors.FindProcessor<>() {
        @Override
        protected boolean accept(Extension extension) {
          final GenericAttributeValue<?> nameElement = getNameElement(extension);
          return nameElement != null && resolveId.equals(nameElement.getStringValue());
        }
      };
    }
    else {
      resolveProcessor = new CommonProcessors.FindFirstProcessor<>();
    }
    processCandidates(resolveProcessor, hasCustomNameElement() ? null : resolveId);

    final Extension value = resolveProcessor.getFoundValue();
    if (value == null) return null;

    final DomTarget target = DomTarget.getTarget(value, getNameElement(value));
    return target != null ? PomService.convertToPsi(target) : value.getXmlElement();
  }

  protected @NotNull @NlsSafe String getResolveValue() {
    return getValue();
  }

  @Nullable
  protected static GenericAttributeValue<?> getAttribute(Extension extension, String attributeName) {
    final DomAttributeChildDescription attributeDescription = extension.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(extension);
  }

  @Nullable
  protected static String getAttributeValue(Extension extension, String attributeName) {
    final GenericAttributeValue attribute = getAttribute(extension, attributeName);
    return attribute == null ? null : attribute.getStringValue();
  }

  protected void processCandidates(Processor<? super Extension> processor) {
    processCandidates(processor, null);
  }

  /**
   * @param extensionPointId To locate a specific instance by ID. Use only when {@link #hasCustomNameElement()} returns {@code false} (using default {@code id} attribute), {@code null} otherwise.
   */
  private void processCandidates(Processor<? super Extension> processor,
                                 @Nullable String extensionPointId) {
    final Project project = myElement.getProject();

    final ExtensionPoint extensionPointDomElement =
      ExtensionPointIndex.findExtensionPoint(project, PluginRelatedLocatorsUtils.getCandidatesScope(project), getExtensionPointFqn());
    if (extensionPointDomElement == null) return;

    final List<ExtensionCandidate> candidates;
    if (extensionPointId == null) {
      candidates = ExtensionLocatorKt.locateExtensionsByExtensionPoint(extensionPointDomElement);
    }
    else {
      candidates = ExtensionLocatorKt.locateExtensionsByExtensionPointAndId(extensionPointDomElement, extensionPointId)
        .findCandidates();
    }

    final DomManager manager = DomManager.getDomManager(project);
    for (ExtensionCandidate candidate : candidates) {
      final XmlTag element = candidate.pointer.getElement();
      final DomElement domElement = manager.getDomElement(element);
      if (domElement instanceof Extension) {
        if (!processor.process((Extension)domElement)) return;
      }
    }
  }
}
