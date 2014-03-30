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
package org.jetbrains.plugins.javaFX;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.util.Map;

/**
 * User: anna
 * Date: 4/2/13
 */
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
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue refId = (XmlAttributeValue)element;
      final PsiReference refIdReference = refId.getReference();
      if (refIdReference != null) {
        final PsiElement resolveRefId = refIdReference.resolve();
        if (resolveRefId instanceof PsiField) {
          allRenames.put(resolveRefId, newName);
        }
      }
    }
  }
}
