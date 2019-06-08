// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.project.Project;
import com.intellij.pom.references.PomService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

abstract class ExtensionPointReferenceBase extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {

  protected ExtensionPointReferenceBase(@NotNull PsiElement element) {
    super(element);
  }

  /**
   * Returns FQN of extension.
   *
   * @return FQN.
   */
  protected abstract String getExtensionPointClassname();

  /**
   * Returns the name attribute for resolving.
   *
   * @see #getAttribute(Extension, String)
   */
  protected abstract GenericAttributeValue<?> getNameElement(Extension extension);

  @Nullable
  @Override
  public PsiElement resolve() {
    final String id = getValue();
    final CommonProcessors.FindProcessor<Extension> resolveProcessor = new CommonProcessors.FindProcessor<Extension>() {
      @Override
      protected boolean accept(Extension extension) {
        final GenericAttributeValue<?> nameElement = getNameElement(extension);
        return nameElement != null && id.equals(nameElement.getStringValue());
      }
    };
    processCandidates(resolveProcessor);

    final Extension value = resolveProcessor.getFoundValue();
    if (value == null) {
      return null;
    }
    final DomTarget target = DomTarget.getTarget(value, getNameElement(value));
    return target != null ? PomService.convertToPsi(target) : value.getXmlElement();
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
    final Project project = myElement.getProject();

    final PsiClass extensionPointClass =
      JavaPsiFacade.getInstance(project).findClass(getExtensionPointClassname(), myElement.getResolveScope());
    if (extensionPointClass == null) return;

    final ExtensionPointLocator extensionPointLocator = new ExtensionPointLocator(extensionPointClass);
    final ExtensionPointCandidate extensionPointCandidate = ContainerUtil.getFirstItem(extensionPointLocator.findDirectCandidates());
    if (extensionPointCandidate == null) return;

    final DomManager manager = DomManager.getDomManager(project);
    final DomElement extensionPointDomElement = manager.getDomElement(extensionPointCandidate.pointer.getElement());
    if (!(extensionPointDomElement instanceof ExtensionPoint)) return;

    for (ExtensionCandidate candidate : ExtensionLocatorKt.locateExtensionsByExtensionPoint((ExtensionPoint)extensionPointDomElement)) {
      final XmlTag element = candidate.pointer.getElement();
      final DomElement domElement = manager.getDomElement(element);
      if (domElement instanceof Extension) {
        if (!processor.process((Extension)domElement)) return;
      }
    }
  }
}
