// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.resources;

import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import static com.intellij.patterns.PlatformPatterns.virtualFile;
import static com.intellij.patterns.StandardPatterns.string;

public class FxmlResourceReferencesContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final XmlAttributeValuePattern attributeValueInFxml = XmlPatterns.xmlAttributeValue().inVirtualFile(
      virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION));

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("%"))
                                          .withParent(XmlPatterns.xmlAttribute().andNot(
                                            XmlPatterns.xmlAttribute().withName(
                                              FxmlConstants.FX_VALUE, FxmlConstants.FX_CONSTANT, FxmlConstants.FX_FACTORY)))
                                          .and(attributeValueInFxml), new JavaFxResourcePropertyReferenceProvider());
  }
}
