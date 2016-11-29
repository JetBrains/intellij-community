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
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * User: anna
 * Date: 1/17/13
 */
public abstract class JavaFxControllerBasedReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public final PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    final XmlAttributeValue xmlAttrVal = (XmlAttributeValue)element;
    final PsiFile containingFile = xmlAttrVal.getContainingFile();
    if (!JavaFxFileTypeFactory.isFxml(containingFile)) return PsiReference.EMPTY_ARRAY;

    final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(containingFile);
    return controllerClass != null ? getReferencesByElement(controllerClass, xmlAttrVal, context) 
                                   : new PsiReference[] {new PsiReferenceBase.Immediate<>(xmlAttrVal, xmlAttrVal)};
  }

  protected abstract PsiReference[] getReferencesByElement(@NotNull PsiClass controllerClass, XmlAttributeValue element, ProcessingContext context);
}
