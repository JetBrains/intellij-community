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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
* User: anna
*/
class ImportReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlProcessingInstruction) {
      final String instructionTarget = JavaFxPsiUtil.getInstructionTarget("import", (XmlProcessingInstruction)parent);
      if (instructionTarget != null && instructionTarget.equals(element.getText())) {
        final PsiReference[] references = FxmlReferencesContributor.CLASS_REFERENCE_PROVIDER.getReferencesByString(instructionTarget, element, 0);
        if (instructionTarget.endsWith(".*")) {
          return ArrayUtil.remove(references, references.length - 1);
        } else {
          return references;
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
