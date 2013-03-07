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

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
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

    registrar.registerReferenceProvider(XmlPatterns.xmlTag().with(inFxmlCondition()),
                                        new MyJavaClassReferenceProvider());

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

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlProcessingInstruction.class).inFile(inFxmlElementPattern()), new ImportReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(attributeValueInFxml),
                                        new EnumeratedAttributeReferenceProvider()); 

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(attributeValueInFxml),
                                        new JavaFxColorReferenceProvider()); 

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_VALUE)
                                                        .withParent(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName(FxmlConstants.STYLESHEETS))))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(true));
  }

  public static PsiFilePattern.Capture<PsiFile> inFxmlElementPattern() {
    return new PsiFilePattern.Capture<PsiFile>(new InitialPatternCondition<PsiFile>(PsiFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof PsiFile && JavaFxFileTypeFactory.isFxml((PsiFile)o);
      }
    });
  }

  public static <T extends XmlElement> PatternCondition<T> inFxmlCondition() {
    return new PatternCondition<T>("inFxmlFile") {
      @Override
      public boolean accepts(@NotNull T value, ProcessingContext context) {
        return JavaFxFileTypeFactory.isFxml(value.getContainingFile());
      }
    };
  }

  private static class MyJavaClassReferenceProvider extends JavaClassReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element) {
      return getReferencesByString(((XmlTag)element).getName(), element, 1);
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByString(String str,
                                                @NotNull final PsiElement position,
                                                int offsetInPosition) {
      final PsiReference[] references = super.getReferencesByString(str, position, offsetInPosition);
      if (references.length <= 1) return PsiReference.EMPTY_ARRAY;
      final PsiReference[] results = new PsiReference[references.length - 1];
      for (int i = 0; i < results.length; i++) {
        results[i] = new JavaClassReferenceWrapper(references[i], position);
      }
      return results;
    }

    private static class JavaClassReferenceWrapper implements PsiReference {
      private final PsiReference myReference;
      private final PsiElement myPosition;

      public JavaClassReferenceWrapper(PsiReference reference, PsiElement position) {
        myReference = reference;
        myPosition = position;
      }

      @Override
      public PsiElement getElement() {
        return myReference.getElement();
      }

      @Override
      public TextRange getRangeInElement() {
        return myReference.getRangeInElement();
      }

      @Nullable
      @Override
      public PsiElement resolve() {
        return myReference.resolve();
      }

      @NotNull
      public String getCanonicalText() {
        return myReference.getCanonicalText();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        String oldText = ((XmlTag)myPosition).getName();
        final TextRange range = getRangeInElement();
        final String newText =
          oldText.substring(0, range.getStartOffset() - 1) + newElementName + oldText.substring(range.getEndOffset() - 1);
        return ((XmlTag)myPosition).setName(newText);
      }

      public PsiElement bindToElement(@NotNull PsiElement element)
        throws IncorrectOperationException {
        String oldText = ((XmlTag)myPosition).getName();
        final TextRange range = getRangeInElement();
        final String newText = ((PsiPackage)element).getQualifiedName() +
                               oldText.substring(range.getEndOffset() - 1);
        return ((XmlTag)myPosition).setName(newText);
      }

      public boolean isReferenceTo(PsiElement element) {
        return myReference.isReferenceTo(element);
      }

      @NotNull
      public Object[] getVariants() {
        return myReference.getVariants();
      }

      public boolean isSoft() {
        return true;
      }
    }
  }
}
