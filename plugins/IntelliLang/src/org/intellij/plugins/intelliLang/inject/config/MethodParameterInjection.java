/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MethodParameterInjection extends BaseInjection<MethodParameterInjection, PsiLiteralExpression> {
  @NotNull
  private String myClassName = "";
  @NotNull
  private String myMethodSignature = "";
  @NotNull
  private boolean[] mySelection = new boolean[0];

  private boolean myApplyInHierarchy = true;

  @NotNull @NonNls
  private String myMethodName = "<none>";

  @NotNull
  public List<TextRange> getInjectedArea(PsiLiteralExpression element) {
    return Collections.singletonList(TextRange.from(1, element.getTextLength() - 2));
  }

  public boolean isApplicable(@NotNull PsiLiteralExpression element) {
    PsiElement e = element;
    while (!(e.getParent() instanceof PsiExpressionList)) {
      e = e.getParent();
      if (!(e instanceof PsiExpression)) {
        return false;
      }
    }

    final PsiParameter parameter = PsiUtilEx.getParameterForArgument((PsiExpression)e);
    if (parameter == null) {
      return false;
    }
    final PsiElement _parent = parameter.getParent();
    final PsiParameterList list;
    if (_parent instanceof PsiParameterList) {
      list = (PsiParameterList)_parent;
    }
    else {
      return false;
    }
    if (mySelection.length != list.getParametersCount()) {
      return false;
    }
    if (!mySelection[list.getParameterIndex(parameter)]) {
      return false;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(list, PsiMethod.class, true, true);
    if (method == null || !myMethodName.equals(method.getName())) {
      return false;
    }
    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) {
      return false;
    }
    if (myClassName.equals(psiClass.getQualifiedName())) {
      return true;
    }
    if (myApplyInHierarchy) {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(element.getProject());
      final PsiClass baseClass = JavaPsiFacade.getInstance(element.getProject()).findClass(myClassName, scope);
      if (baseClass != null && psiClass.isInheritor(baseClass, true)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }

  public void setClassName(@NotNull String className) {
    myClassName = className;
  }

  @NotNull
  public String getMethodSignature() {
    return myMethodSignature;
  }

  public void setMethodSignature(@Nullable String methodSignature) {
    myMethodSignature = methodSignature != null ? methodSignature : "";
    updateMethodName();
  }

  private void updateMethodName() {
    final String s = myMethodSignature.split("\\(", 2)[0];
    if (s.length() != 0) {
      myMethodName = s;
    }
    else {
      myMethodName = "<none>";
    }
  }

  @NotNull
  public boolean[] getSelection() {
    return mySelection;
  }

  public void setSelection(@NotNull boolean[] indices) {
    mySelection = indices;
  }

  public boolean isApplyInHierarchy() {
    return myApplyInHierarchy;
  }

  public void setApplyInHierarchy(boolean applyInHierarchy) {
    myApplyInHierarchy = applyInHierarchy;
  }

  public void copyFrom(@NotNull MethodParameterInjection other) {
    super.copyFrom(other);
    myClassName = other.getClassName();
    myMethodSignature = other.getMethodSignature();
    mySelection = other.getSelection();
    myApplyInHierarchy = other.isApplyInHierarchy();
  }

  protected void readExternalImpl(Element e) throws InvalidDataException {
    setClassName(JDOMExternalizer.readString(e, "CLASS"));
    setMethodSignature(JDOMExternalizer.readString(e, "METHOD"));
    setApplyInHierarchy(JDOMExternalizer.readBoolean(e, "APPLY_IN_HIERARCHY"));

    final JDOMExternalizableStringList list = new JDOMExternalizableStringList();
    list.readExternal(e);
    final Boolean[] booleans = new Boolean[list.size()];
    ContainerUtil.map2Array(list, booleans, new Function<String, Boolean>() {
      public Boolean fun(String s) {
        return Boolean.valueOf(s);
      }
    });
    mySelection = new boolean[booleans.length];
    for (int i = 0; i < mySelection.length; i++) {
      mySelection[i] = booleans[i];
    }
  }

  protected void writeExternalImpl(Element e) throws WriteExternalException {
    JDOMExternalizer.write(e, "CLASS", myClassName);
    JDOMExternalizer.write(e, "METHOD", myMethodSignature);
    JDOMExternalizer.write(e, "APPLY_IN_HIERARCHY", myApplyInHierarchy);

    final Boolean[] booleans = new Boolean[mySelection.length];
    for (int i = 0; i < mySelection.length; i++) {
      booleans[i] = mySelection[i];
    }
    //noinspection MismatchedQueryAndUpdateOfCollection
    final JDOMExternalizableStringList list = new JDOMExternalizableStringList();
    list.addAll(ContainerUtil.map2List(booleans, new Function<Boolean, String>() {
      public String fun(Boolean s) {
        return s.toString();
      }
    }));
    list.writeExternal(e);
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final MethodParameterInjection that = (MethodParameterInjection)o;

    if (!myClassName.equals(that.myClassName)) return false;
    if (!myMethodSignature.equals(that.myMethodSignature)) return false;
    if (myApplyInHierarchy != that.myApplyInHierarchy) return false;
    if (!Arrays.equals(mySelection, that.mySelection)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myClassName.hashCode();
    result = 31 * result + myMethodSignature.hashCode();
    result = 31 * result + (myApplyInHierarchy ? 0 : 1);
    result = 31 * result + Arrays.hashCode(mySelection);
    return result;
  }

  @NotNull
  private String getMethodName() {
    updateMethodName();
    return myMethodName;
  }

  public String getDisplayName() {
    final String name = getMethodName();
    final String clazz = StringUtil.getShortName(getClassName());
    return clazz.length() > 0 ? clazz + "." + name : name;
  }

}

