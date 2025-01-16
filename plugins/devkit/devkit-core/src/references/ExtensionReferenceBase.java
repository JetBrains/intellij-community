// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

abstract class ExtensionReferenceBase extends PsiReferenceBase<PsiElement> implements PluginConfigReference {

  protected ExtensionReferenceBase(@NotNull PsiElement element) {
    super(element);
  }

  protected ExtensionReferenceBase(@NotNull PsiElement element, TextRange rangeInElement) {
    super(element, rangeInElement);
  }

  /**
   * @see ExtensionPoint#getEffectiveQualifiedName()
   */
  protected abstract String getExtensionPointFqn();

  /**
   * Returns the name attribute for resolving, by default {@link Extension#getId()}.
   *
   * @see #getAttribute(Extension, String)
   */
  protected @Nullable GenericAttributeValue<String> getNameElement(Extension extension) {
    return extension.getId();
  }

  @Override
  public @Nullable PsiElement resolve() {
    final String resolveId = getResolveValue();
    if (StringUtil.isEmptyOrSpaces(resolveId)) return null;

    final CommonProcessors.FindProcessor<Extension> resolveProcessor = new CommonProcessors.FindFirstProcessor<>();
    ExtensionLocatorKt.processExtensionCandidates(myElement.getProject(), getExtensionPointFqn(), resolveProcessor,
                                                  resolveId, extension -> getNameElement(extension));

    final Extension value = resolveProcessor.getFoundValue();
    if (value == null) return null;

    final DomTarget target = DomTarget.getTarget(value, getNameElement(value));
    return target != null ? PomService.convertToPsi(target) : value.getXmlElement();
  }

  protected @NotNull @NlsSafe String getResolveValue() {
    return getValue();
  }

  protected static @Nullable GenericAttributeValue<?> getAttribute(Extension extension, String attributeName) {
    final DomAttributeChildDescription attributeDescription = extension.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(extension);
  }

  protected static @Nullable String getAttributeValue(Extension extension, String attributeName) {
    final GenericAttributeValue attribute = getAttribute(extension, attributeName);
    return attribute == null ? null : attribute.getStringValue();
  }

  protected void processCandidates(Processor<? super Extension> processor) {
    ExtensionLocatorKt.processExtensionCandidates(myElement.getProject(), getExtensionPointFqn(), processor,
                                                  null, null);
  }
}
