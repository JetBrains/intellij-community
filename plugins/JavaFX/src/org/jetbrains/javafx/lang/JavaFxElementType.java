/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.javafx.lang;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxLanguage;

import java.lang.reflect.Constructor;

/**
 * JavaFx element type
 *
 * @author Alexey.Ivanov
 */
public class JavaFxElementType extends IElementType {
  private static final Class[] PARAMETER_TYPES = new Class[]{ASTNode.class};
  protected Class<? extends PsiElement> myPsiElementClass;
  private Constructor<? extends PsiElement> myConstructor;

  public JavaFxElementType(@NotNull @NonNls String debugName) {
    super(debugName, JavaFxLanguage.getInstance());
  }

  public JavaFxElementType(@NonNls String debugName, Class<? extends PsiElement> psiElementClass) {
    this(debugName);
    myPsiElementClass = psiElementClass;
  }

  @Nullable
  public PsiElement createElement(ASTNode node) {
    if (myPsiElementClass == null) {
      return null;
    }

    try {
      if (myConstructor == null) {
        myConstructor = myPsiElementClass.getConstructor(PARAMETER_TYPES);
      }

      return myConstructor.newInstance(node);
    }
    catch (Exception e) {
      throw new IllegalStateException("No necessary constructor for " + node.getElementType(), e);
    }
  }

  @Override
  public String toString() {
    return "JavaFx:" + super.toString();
  }
}
