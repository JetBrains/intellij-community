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
package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.indexing.JavaFxControllerClassIndex;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRenameFxIdFieldProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final NestedControllerCandidate nestedControllerCandidate = findNestedControllerCandidate(element);
    final Collection<PsiFile> fxmls = findFxmlWithController(nestedControllerCandidate);
    return !fxmls.isEmpty();
  }

  @Override
  public boolean isInplaceRenameSupported() {
    return false;
  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
    final NestedControllerCandidate nestedControllerCandidate = findNestedControllerCandidate(element);
    if (nestedControllerCandidate != null) {
      final Collection<PsiFile> fxmls = findFxmlWithController(nestedControllerCandidate);
      for (PsiFile fxml : fxmls) {
        final Ref<Boolean> found = new Ref<>(false);
        fxml.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            if (found.get()) return;
            if (FxmlConstants.FX_INCLUDE.equals(tag.getName())) {
              final String value = tag.getAttributeValue(FxmlConstants.FX_ID);
              if (StringUtil.equals(nestedControllerCandidate.fxId, value)) {
                found.set(true);
              }
            }
          }
        });
        if (found.get()) {
          allRenames.put(nestedControllerCandidate.nestedControllerField, newName + FxmlConstants.CONTROLLER_SUFFIX);
          return;
        }
      }
    }
  }

  @Nullable
  private static NestedControllerCandidate findNestedControllerCandidate(@NotNull PsiElement element) {
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      final String fxId = field.getName();
      if (!StringUtil.isEmpty(fxId)) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          final PsiField nestedControllerField = containingClass.findFieldByName(fxId + FxmlConstants.CONTROLLER_SUFFIX, true);
          if (nestedControllerField != null) {
            final PsiType psiType = nestedControllerField.getType();
            if (!(psiType instanceof PsiPrimitiveType) && !(psiType instanceof PsiArrayType)) { // optimization
              return new NestedControllerCandidate(fxId, nestedControllerField, containingClass);
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Collection<PsiFile> findFxmlWithController(@Nullable NestedControllerCandidate nestedControllerCandidate) {
    if (nestedControllerCandidate != null) {
      final String qualifiedName = nestedControllerCandidate.controllerClass.getQualifiedName();
      if (qualifiedName != null) {
        final Project project = nestedControllerCandidate.controllerClass.getProject();
        return JavaFxControllerClassIndex.findFxmlWithController(project, qualifiedName);
      }
    }
    return Collections.emptyList();
  }

  private static class NestedControllerCandidate {
    private final String fxId;
    private final PsiField nestedControllerField;
    private final PsiClass controllerClass;

    private NestedControllerCandidate(@NotNull String fxId, @NotNull PsiField nestedControllerField, @NotNull PsiClass controllerClass) {
      this.fxId = fxId;
      this.nestedControllerField = nestedControllerField;
      this.controllerClass = controllerClass;
    }
  }
}
