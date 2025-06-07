// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.function.Function;

import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.*;

final class IconsXmlReferencesContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerForIconXmlAttribute(registrar);
  }

  private static void registerForIconXmlAttribute(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withLocalName("icon"), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!PsiUtil.isPluginXmlPsiElement(element)) {
          return PsiReference.EMPTY_ARRAY;
        }

        return new PsiReference[]{
          new IconPsiReferenceBase(element) {
            @Override
            public PsiElement resolve() {
              String value = ((XmlAttributeValue)element).getValue();
              if (value.startsWith("/")) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef != null ? lastRef.resolve() : null;
              }

              return resolveIconPath(value, element);
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
              PsiElement element = resolve();
              PsiElement resultForFile = handleFile(element, lastRef -> lastRef.handleElementRename(newElementName));
              if (resultForFile != null) {
                return resultForFile;
              }

              PsiElement resultForField = handleField(element, newElementName);
              if (resultForField != null) {
                return resultForField;
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              PsiElement resultForFile = handleFile(element, lastRef -> lastRef.bindToElement(element));
              if (resultForFile != null) {
                return resultForFile;
              }

              PsiElement resultForField = handleField(element, null);
              if (resultForField != null) {
                return resultForField;
              }

              return super.bindToElement(element);
            }

            private static PsiElement handleFile(PsiElement element, Function<FileReference, PsiElement> callback) {
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                if (lastRef != null) {
                  return callback.apply(lastRef);
                }
              }
              return null;
            }

            private @Nullable PsiElement handleField(PsiElement element, @Nullable String newElementName) {
              if (element instanceof PsiField) {
                PsiClass containingClass = ((PsiField)element).getContainingClass();
                if (containingClass != null) {
                  String classQualifiedName = containingClass.getQualifiedName();
                  if (classQualifiedName != null) {
                    if (newElementName == null) {
                      newElementName = ((PsiField)element).getName();
                    }
                    if (classQualifiedName.startsWith(COM_INTELLIJ_ICONS_PREFIX)) {
                      return replace(classQualifiedName, newElementName, COM_INTELLIJ_ICONS_PREFIX);
                    }
                    if (classQualifiedName.startsWith(ICONS_PACKAGE_PREFIX)) {
                      return replace(classQualifiedName, newElementName, ICONS_PACKAGE_PREFIX);
                    }
                    return ElementManipulators.handleContentChange(myElement, classQualifiedName + "." + newElementName);
                  }
                }
              }
              return null;
            }

            private PsiElement replace(@NonNls String fqn, @NonNls String newName, @NonNls String pckg) {
              XmlAttribute parent = (XmlAttribute)getElement().getParent();
              parent.setValue(fqn.substring(pckg.length()) + "." + newName);
              return parent.getValueElement();
            }
          }
        };
      }
    });
  }
}
