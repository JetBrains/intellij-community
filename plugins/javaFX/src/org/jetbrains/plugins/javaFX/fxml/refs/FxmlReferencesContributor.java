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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import static com.intellij.patterns.StandardPatterns.string;

/**
 * User: anna
 * Date: 1/14/13
 */
public class FxmlReferencesContributor extends PsiReferenceContributor {
  public static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final XmlAttributeValuePattern attributeValueInFxml = XmlPatterns.xmlAttributeValue().with(inFxmlCondition());
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_CONTROLLER))
                                          .and(attributeValueInFxml),
                                        CLASS_REFERENCE_PROVIDER);

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName("type")
                                                        .withParent(XmlPatterns.xmlTag().withName(FxmlConstants.FX_ROOT)))
                                          .and(attributeValueInFxml),
                                        CLASS_REFERENCE_PROVIDER);

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_ID))
                                          .and(attributeValueInFxml),
                                        new JavaFxFieldIdReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_ELEMENT_SOURCE)
                                                                                     .withParent(XmlPatterns.xmlTag()
                                                                                                   .withName(FxmlConstants.FX_INCLUDE)))
                                          .and(attributeValueInFxml),
                                        new JavaFxSourceReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_ELEMENT_SOURCE)
                                                                                     .withParent(XmlPatterns.xmlTag()
                                                                                                   .withName(FxmlConstants.FX_SCRIPT)))
                                          .and(attributeValueInFxml),
                                        new JavaFxSourceReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_ELEMENT_SOURCE)
                                                                                     .withParent(XmlPatterns.xmlTag()
                                                                                                   .withName(FxmlConstants.FX_REFERENCE)))
                                          .and(attributeValueInFxml),
                                        new JavaFxComponentIdReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_FACTORY))
                                          .and(attributeValueInFxml),
                                        new JavaFxFactoryReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("#"))
                                          .and(attributeValueInFxml),
                                        new JavaFxEventHandlerReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("@")).and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("$")).and(attributeValueInFxml),
                                        new JavaFxComponentIdReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName("url")).and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider());
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.STYLESHEETS)).and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(true));

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlTokenType.XML_TAG_CHARACTERS).inFile(inFxmlElementPattern()), new ImportReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(attributeValueInFxml),
                                        new EnumeratedAttributeReferenceProvider());
  }

  public static PsiFilePattern.Capture<PsiFile> inFxmlElementPattern() {
    return new PsiFilePattern.Capture<PsiFile>(new InitialPatternCondition<PsiFile>(PsiFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof PsiFile && JavaFxFileTypeFactory.isFxml((PsiFile)o);
      }
    });
  }

  public static PatternCondition<XmlAttributeValue> inFxmlCondition() {
    return new PatternCondition<XmlAttributeValue>("inFxmlFile") {
      @Override
      public boolean accepts(@NotNull XmlAttributeValue value, ProcessingContext context) {
        return JavaFxFileTypeFactory.isFxml(value.getContainingFile());
      }
    };
  }
}
