// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.util.Map;

public class JavaFxRenameAttributeProcessor extends RenameXmlAttributeProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (element instanceof XmlAttributeValue && JavaFxFileTypeFactory.isFxml(element.getContainingFile())) {
      final PsiElement parent = element.getParent();
      return parent instanceof XmlAttribute && FxmlConstants.FX_ID.equals(((XmlAttribute)parent).getName());
    }
    return false;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames, @NotNull SearchScope scope) {
    visitReferencedElements(element.getReferences(), psiElement -> {
      if (psiElement instanceof PsiNamedElement && psiElement != element) {
        allRenames.put(psiElement, newName);
      }
    });
  }

  static void visitReferencedElements(PsiReference[] references, NullableConsumer<PsiElement> consumer) {
    for (PsiReference reference : references) {
      if (reference instanceof PsiPolyVariantReference) {
        final ResolveResult[] resolveResults = ((PsiPolyVariantReference)reference).multiResolve(false);
        for (ResolveResult resolveResult : resolveResults) {
          consumer.consume(resolveResult.getElement());
        }
      }
      else {
        consumer.consume(reference.resolve());
      }
    }
  }
}
