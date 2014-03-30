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
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import static com.intellij.patterns.PlatformPatterns.virtualFile;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * User: anna
 * Date: 1/14/13
 */
public class FxmlReferencesContributor extends PsiReferenceContributor {
  public static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final XmlAttributeValuePattern attributeValueInFxml = XmlPatterns.xmlAttributeValue().inVirtualFile(
      virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION));
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_CONTROLLER))
                                          .and(attributeValueInFxml),
                                        CLASS_REFERENCE_PROVIDER);

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName("type")
                                                        .withParent(XmlPatterns.xmlTag().withName(FxmlConstants.FX_ROOT)))
                                          .and(attributeValueInFxml),
                                        CLASS_REFERENCE_PROVIDER);

    registrar.registerReferenceProvider(XmlPatterns.xmlTag().inVirtualFile(virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION)),
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
                                                                                                   .withName(string().oneOf(FxmlConstants.FX_REFERENCE, FxmlConstants.FX_COPY))))
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
                                        new JavaFxLocationReferenceProvider(false, "png"));
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.STYLESHEETS)).and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(true, "css"));

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlProcessingInstruction.class).inVirtualFile(virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION)),
                                        new ImportReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(attributeValueInFxml),
                                        new JavaFxColorReferenceProvider()); 

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_VALUE)
                                                        .withParent(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName(FxmlConstants.STYLESHEETS))))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(true, "css"));
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
        final PsiElement resolve = myReference.resolve();
        if (resolve != null) {
          return resolve;
        }
        return getReferencedClass();
      }

      private PsiElement getReferencedClass() {
        if (myPosition instanceof XmlTag) {
          final XmlElementDescriptor descriptor = ((XmlTag)myPosition).getDescriptor();
          if (descriptor != null) {
            final PsiElement declaration = descriptor.getDeclaration();
            if (declaration instanceof PsiMethod &&
                ((PsiMethod)declaration).hasModifierProperty(PsiModifier.STATIC)) {
              final PsiClass containingClass = ((PsiMethod)declaration).getContainingClass();
              if (containingClass != null && myReference.getCanonicalText().equals(containingClass.getName())) {
                return containingClass;
              }
            }
          }
        }
        return null;
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
        final String newText = (element instanceof PsiPackage ? ((PsiPackage)element).getQualifiedName() : ((PsiClass)element).getName()) +
                               oldText.substring(range.getEndOffset() - 1);
        return ((XmlTag)myPosition).setName(newText);
      }

      public boolean isReferenceTo(PsiElement element) {
        return myReference.isReferenceTo(element) || getReferencedClass() == element;
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
