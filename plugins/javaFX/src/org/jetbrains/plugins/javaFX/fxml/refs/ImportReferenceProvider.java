// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

final class ImportReferenceProvider extends PsiReferenceProvider {

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof XmlProcessingInstruction) {
      final ASTNode importNode = element.getNode().findChildByType(XmlTokenType.XML_TAG_CHARACTERS);
      if (importNode != null) {
        final PsiElement importInstr = importNode.getPsi();
        final String instructionTarget = JavaFxPsiUtil.getInstructionTarget("import", (XmlProcessingInstruction)element);
        if (instructionTarget != null && instructionTarget.equals(importInstr.getText())) {
          final PsiReference[] references =
            FxmlReferencesContributor.CLASS_REFERENCE_PROVIDER.getReferencesByString(instructionTarget, element, importInstr.getStartOffsetInParent());
          if (instructionTarget.endsWith(".*")) {
            return ArrayUtil.remove(references, references.length - 1);
          }
          else {
            return references;
          }
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
