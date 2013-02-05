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

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
* User: anna
* Date: 1/17/13
*/
class JavaFxFieldIdReferenceProvider extends JavaFxControllerBasedReferenceProvider {
  @Override
  protected PsiReference[] getReferencesByElement(@NotNull final PsiClass aClass,
                                                  final XmlAttributeValue xmlAttributeValue,
                                                  ProcessingContext context) {
    final PsiField field = aClass.findFieldByName(xmlAttributeValue.getValue(), false);
    return new PsiReference[] {new PsiReferenceBase<XmlAttributeValue>(xmlAttributeValue) {
      @Nullable
      @Override
      public PsiElement resolve() {
        return field != null ? field : xmlAttributeValue;
      }

      @NotNull
      @Override
      public Object[] getVariants() {
        final List<Object> fieldsToSuggest = new ArrayList<Object>();
        final PsiField[] fields = aClass.getFields();
        for (PsiField psiField : fields) {
          if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
            if (JavaFxPsiUtil.isVisibleInFxml(psiField)) {
              fieldsToSuggest.add(psiField);
            }
          }
        }
        return ArrayUtil.toObjectArray(fieldsToSuggest);
      }
    }};
  }
}
