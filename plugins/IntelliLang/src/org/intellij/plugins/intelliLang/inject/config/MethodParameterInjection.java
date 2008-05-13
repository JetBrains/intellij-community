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
import gnu.trove.THashMap;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class MethodParameterInjection extends BaseInjection<MethodParameterInjection, PsiLiteralExpression> {
  @NotNull
  private String myClassName = "";

  @NotNull
  private final Map<String, MethodInfo> myParameterMap = new THashMap<String, MethodInfo>();

  private boolean myApplyInHierarchy = true;

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
    final PsiMethod method = PsiTreeUtil.getParentOfType(list, PsiMethod.class, true, true);
    if (method == null) return false;
    final String methodName = method.getName();
    final int parameterIndex = list.getParameterIndex(parameter);
    boolean found = false;
    for (MethodInfo info : myParameterMap.values()) {
      if (info.methodName.equals(methodName)
          && info.paramFlags.length == list.getParametersCount()
          && info.paramFlags[parameterIndex]) {
        found = true;
        break;
      }
    }
    if (!found) return false;
    final PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    if (myClassName.equals(psiClass.getQualifiedName())) return true;
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

  public void setMethodInfos(final Collection<MethodInfo> newInfos) {
    myParameterMap.clear();
    for (MethodInfo methodInfo : newInfos) {
      myParameterMap.put(methodInfo.getMethodSignature(), methodInfo);
    }
  }

  public Collection<MethodInfo> getMethodInfos() {
    return myParameterMap.values();
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
    myParameterMap.clear();
    for (MethodInfo info : other.myParameterMap.values()) {
      myParameterMap.put(info.methodSignature, info.copy());
    }
    myApplyInHierarchy = other.isApplyInHierarchy();
  }

  protected void readExternalImpl(Element e) throws InvalidDataException {
    setClassName(JDOMExternalizer.readString(e, "CLASS"));
    setApplyInHierarchy(JDOMExternalizer.readBoolean(e, "APPLY_IN_HIERARCHY"));
    readOldFormat(e);
    final THashMap<String, String> map = new THashMap<String, String>();
    JDOMExternalizer.readMap(e, map, null, "SIGNATURES");
    for (String s : map.keySet()) {
      final String fixedSignature = fixSignature(s, false);
      myParameterMap.put(fixedSignature, new MethodInfo(fixedSignature, map.get(s)));
    }
  }

  private void readOldFormat(final Element e) throws InvalidDataException {
    final JDOMExternalizableStringList list = new JDOMExternalizableStringList();
    list.readExternal(e);
    if (list.isEmpty()) return;
    final boolean[] selection = new boolean[list.size()];
    for (int i = 0; i < list.size(); i++) {
      selection[i] = Boolean.parseBoolean(list.get(i));
    }
    final String methodSignature = fixSignature(JDOMExternalizer.readString(e, "METHOD"), false);
    myParameterMap.put(methodSignature, new MethodInfo(methodSignature, selection));
  }

  protected void writeExternalImpl(Element e) throws WriteExternalException {
    JDOMExternalizer.write(e, "CLASS", myClassName);
    JDOMExternalizer.write(e, "APPLY_IN_HIERARCHY", myApplyInHierarchy);
    final THashMap<String, String> map = new THashMap<String, String>();
    for (String s : myParameterMap.keySet()) {
      map.put(s, myParameterMap.get(s).getFlagsString());
    }
    JDOMExternalizer.writeMap(e, map, null, "SIGNATURES");
  }


  @Override
  public MethodParameterInjection copy() {
    final MethodParameterInjection result = new MethodParameterInjection();
    result.copyFrom(this);
    return result;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final MethodParameterInjection that = (MethodParameterInjection)o;

    if (!myClassName.equals(that.myClassName)) return false;
    if (!myParameterMap.equals(that.myParameterMap)) return false;
    if (myApplyInHierarchy != that.myApplyInHierarchy) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myClassName.hashCode();
    result = 31 * result + myParameterMap.hashCode();
    result = 31 * result + (myApplyInHierarchy ? 0 : 1);
    return result;
  }

  public String getDisplayName() {
    final String className = getClassName();
    if (StringUtil.isEmpty(className)) return "<unnamed>";
    MethodInfo singleInfo = null;
    main : for (MethodInfo info : myParameterMap.values()) {
      for (boolean b : info.paramFlags) {
        if (b) {
          if (singleInfo == null) {
            singleInfo = info;
            break;
          }
          else {
            singleInfo = null;
            break main;
          }
        }
      }
    }
    final String name = singleInfo != null
                        ? StringUtil.getShortName(className) + "." + singleInfo.methodName
                        : StringUtil.getShortName(className);
    return name + " ("+StringUtil.getPackageName(className)+")";
  }

  public static String fixSignature(final String signature, final boolean parameterNames) {
    @NonNls final StringBuilder sb = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(signature, "(,)");
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; st.hasMoreTokens(); i++) {
      final String token = st.nextToken().trim();
      final int idx;
      if (i > 1) sb.append(", ");
      if (i == 0) {
        sb.append(token).append("(");
      }
      else if ((idx = token.indexOf(' ')) > -1) {
        if (parameterNames) {
          sb.append(token);
        }
        else {
          sb.append(token.substring(0, idx));
        }
      }
      else {
        sb.append(token);
        if (parameterNames) {
          sb.append(' ').append('p').append(i);
        }
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static class MethodInfo {
    @NotNull
    final String methodSignature;
    @NotNull
    final String methodName;
    @NotNull
    final boolean[] paramFlags;

    public MethodInfo(@NotNull final String methodSignature, @NotNull final boolean[] paramFlags) {
      this.methodSignature = methodSignature;
      this.paramFlags = paramFlags;
      methodName = calcMethodName(methodSignature);
    }

    public MethodInfo(@NotNull final String methodSignature, @NotNull final String paramFlags) {
      this.methodSignature = methodSignature;
      this.paramFlags = calcParamFlags(paramFlags);
      methodName = calcMethodName(methodSignature);
    }

    @NotNull
    public String getMethodSignature() {
      return methodSignature;
    }

    @NotNull
    public boolean[] getParamFlags() {
      return paramFlags;
    }

    public boolean isEnabled() {
      for (boolean b : paramFlags) {
        if (b) return true;
      }
      return false;
    }

    private static boolean[] calcParamFlags(final String string) {
      final StringTokenizer st = new StringTokenizer(string, ",");
      final boolean[] result = new boolean[st.countTokens()];
      for (int i = 0; i < result.length; i++) {
        result[i] = Boolean.parseBoolean(st.nextToken());
      }
      return result;
    }

    private static String calcMethodName(final String methodSignature) {
      final String s = methodSignature.split("\\(", 2)[0];
      return s.length() != 0 ? s : "<none>";
    }

    public String getFlagsString() {
      final StringBuilder result = new StringBuilder();
      boolean first = true;
      for (boolean b : paramFlags) {
        if (first) first = false;
        else result.append(',');
        result.append(b);
      }
      return result.toString();
    }

    public MethodInfo copy() {
      return new MethodInfo(methodSignature, paramFlags.clone());
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodInfo that = (MethodInfo)o;

      if (!methodName.equals(that.methodName)) return false;
      if (!methodSignature.equals(that.methodSignature)) return false;
      if (!Arrays.equals(paramFlags, that.paramFlags)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = methodSignature.hashCode();
      result = 31 * result + methodName.hashCode();
      result = 31 * result + Arrays.hashCode(paramFlags);
      return result;
    }
  }

}

