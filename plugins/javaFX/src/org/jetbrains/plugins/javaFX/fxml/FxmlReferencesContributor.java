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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.string;

/**
 * User: anna
 * Date: 1/14/13
 */
public class FxmlReferencesContributor extends PsiReferenceContributor {
  public static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_CONTROLLER)),
                                        CLASS_REFERENCE_PROVIDER);

    final XmlAttributeValuePattern attributeValuePattern = XmlPatterns.xmlAttributeValue().withValue(string().startsWith("#"))
      .and(XmlPatterns.xmlAttributeValue().with(inFxmlCondition()));
    registrar.registerReferenceProvider(attributeValuePattern,
                                        new JavaFxEventHandlerReferenceProvider());
  }

  private static PatternCondition<XmlAttributeValue> inFxmlCondition() {
    return new PatternCondition<XmlAttributeValue>("inFxmlFile") {
      @Override
      public boolean accepts(@NotNull XmlAttributeValue value, ProcessingContext context) {
        return JavaFxFileTypeFactory.isFxml(value.getContainingFile());
      }
    };
  }
}
