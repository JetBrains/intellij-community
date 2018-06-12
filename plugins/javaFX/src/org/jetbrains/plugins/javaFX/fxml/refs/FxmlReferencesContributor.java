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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import static com.intellij.patterns.PlatformPatterns.virtualFile;
import static com.intellij.patterns.StandardPatterns.string;

public class FxmlReferencesContributor extends PsiReferenceContributor {
  public static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final XmlAttributeValuePattern attributeValueInFxml = XmlPatterns.xmlAttributeValue().inVirtualFile(
      virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION));
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_CONTROLLER))
                                          .and(attributeValueInFxml),
                                        CLASS_REFERENCE_PROVIDER);

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName("type")
                                                        .withParent(XmlPatterns.xmlTag().withName(FxmlConstants.FX_ROOT)))
                                          .and(attributeValueInFxml),
                                        new MyJavaClassReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlTag().inVirtualFile(virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION)),
                                        new MyJavaClassReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_ID))
                                          .and(attributeValueInFxml),
                                        new JavaFxFieldIdReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.SOURCE)
                                                                                     .withParent(XmlPatterns.xmlTag()
                                                                                                   .withName(FxmlConstants.FX_INCLUDE)))
                                          .and(attributeValueInFxml),
                                        new JavaFxSourceReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.SOURCE)
                                                                                     .withParent(XmlPatterns.xmlTag()
                                                                                                   .withName(FxmlConstants.FX_SCRIPT)))
                                          .and(attributeValueInFxml),
                                        new JavaFxSourceReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.SOURCE)
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

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("@"))
                                          .withParent(StandardPatterns.not(XmlPatterns.xmlAttribute().withName(FxmlConstants.STYLESHEETS)))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("$"))
                                          .withParent(XmlPatterns.xmlAttribute()
                                                        .andNot(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_VALUE)))
                                          .and(attributeValueInFxml),
                                        new JavaFxComponentIdReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.URL_ATTR))
                                          .andNot(XmlPatterns.xmlAttributeValue().withValue(string().matches("^https?://.*")))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(false, "png", "jpg", "gif", "bmp"));
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.STYLESHEETS)).and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(true, "css"));

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withValue(string().startsWith("@"))
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.VALUE)
                                                        .withParent(XmlPatterns.xmlTag().withName(FxmlConstants.URL_TAG)
                                                                      .withParent(XmlPatterns.xmlTag().withName(FxmlConstants.STYLESHEETS))))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(false, "css"));

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlProcessingInstruction.class).inVirtualFile(virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION)),
                                        new ImportReferenceProvider());

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().and(attributeValueInFxml),
                                        new JavaFxColorReferenceProvider()); 

    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue()
                                          .withParent(XmlPatterns.xmlAttribute().withName(FxmlConstants.FX_VALUE)
                                                        .withParent(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName(FxmlConstants.STYLESHEETS))))
                                          .and(attributeValueInFxml),
                                        new JavaFxLocationReferenceProvider(false, "css"));

    registrar.registerReferenceProvider(XmlPatterns.xmlAttribute().withLocalName(string().contains("."))
                                          .inVirtualFile(virtualFile().withExtension(JavaFxFileTypeFactory.FXML_EXTENSION)),
                                        new JavaFxStaticPropertyReferenceProvider());
  }

  private static class MyJavaClassReferenceProvider extends JavaClassReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element) {
      String name = element instanceof XmlAttributeValue ? ((XmlAttributeValue)element).getValue() 
                                                         : ((XmlTag)element).getName();
      return getReferencesByString(name, element, 1);
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByString(String str,
                                                @NotNull final PsiElement position,
                                                int offsetInPosition) {
      if (str.length() == 0) return PsiReference.EMPTY_ARRAY;
      final PsiReference[] references = super.getReferencesByString(str, position, offsetInPosition);
      final int offset = position instanceof XmlTag ? 1 : 0;
      if (references.length <= offset) return PsiReference.EMPTY_ARRAY;
      final PsiReference[] results = new PsiReference[references.length - offset];
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

      @NotNull
      @Override
      public PsiElement getElement() {
        return myReference.getElement();
      }

      @NotNull
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
        else if (myPosition instanceof XmlAttributeValue) {
          return JavaFxPsiUtil.findPsiClass(((XmlAttributeValue)myPosition).getValue(), myPosition);
        }
        return null;
      }

      @NotNull
      public String getCanonicalText() {
        return myReference.getCanonicalText();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        String oldText = getOldName();
        final TextRange range = getRangeInElement();
        final String newText =
          oldText.substring(0, range.getStartOffset() - 1) + newElementName + oldText.substring(range.getEndOffset() - 1);
        return setNewName(newText);
      }

      public PsiElement bindToElement(@NotNull PsiElement element)
        throws IncorrectOperationException {
        String oldText = getOldName();
        final TextRange range = getRangeInElement();
        final String newText = (element instanceof PsiPackage ? ((PsiPackage)element).getQualifiedName() : ((PsiClass)element).getName()) +
                               oldText.substring(range.getEndOffset() - 1);
        return setNewName(newText);
      }

      private PsiElement setNewName(String newText) {
        if (myPosition instanceof XmlTag) {
          return ((XmlTag)myPosition).setName(newText);
        }
        else {
          final XmlElementFactory xmlElementFactory = XmlElementFactory.getInstance(myPosition.getProject());
          final XmlAttribute xmlAttribute = xmlElementFactory.createXmlAttribute("attributeName", newText);
          final XmlAttributeValue valueElement = xmlAttribute.getValueElement();
          assert valueElement != null;
          return myPosition.replace(valueElement);
        }
      }

      private String getOldName() {
        return myPosition instanceof XmlTag ? ((XmlTag)myPosition).getName() : ((XmlAttributeValue)myPosition).getValue();
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
