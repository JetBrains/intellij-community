// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxStaticPropertyReference extends JavaFxPropertyReference<XmlAttribute> {
  private final String myPropertyName;
  private final PsiMethod myStaticMethod;

  public JavaFxStaticPropertyReference(@NotNull XmlAttribute xmlAttribute,
                                       @Nullable PsiClass psiClass,
                                       @NotNull String propertyName) {
    super(xmlAttribute, psiClass);
    myPropertyName = propertyName;
    myStaticMethod = JavaFxPsiUtil.findStaticPropertySetter(propertyName, psiClass);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myStaticMethod;
  }

  @Nullable
  @Override
  public PsiMethod getGetter() {
    return null;
  }

  @Nullable
  @Override
  public PsiMethod getSetter() {
    return null;
  }

  @Nullable
  @Override
  public PsiField getField() {
    return null;
  }

  @Nullable
  @Override
  public PsiMethod getObservableGetter() {
    return null;
  }

  @Nullable
  @Override
  public PsiMethod getStaticSetter() {
    return myStaticMethod;
  }

  @Override
  public PsiType getType() {
    if (myStaticMethod != null) {
      final PsiParameter[] parameters = myStaticMethod.getParameterList().getParameters();
      if (parameters.length == 2) {
        return parameters[1].getType();
      }
    }
    return null;
  }

  @Override
  public String getPropertyName() {
    return myPropertyName;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final String newPropertyName = JavaFxPsiUtil.getPropertyName(newElementName, true);
    return super.handleElementRename(newPropertyName);
  }
}
