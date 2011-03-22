/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyNamedArgumentProvider {

  public static final ExtensionPointName<GroovyNamedArgumentProvider> EP_NAME = ExtensionPointName.create("org.intellij.groovy.namedArgumentProvider");

  public abstract void getNamedArguments(@Nullable GrCall call, @NotNull PsiMethod method, Map<String, Condition<PsiType>> result);

  public static Map<String, Condition<PsiType>> getNamedArguments(@Nullable GrCall call, @NotNull PsiMethod method) {
    Map<String, Condition<PsiType>> namedArguments = new HashMap<String, Condition<PsiType>>();

    for (GroovyNamedArgumentProvider namedArgumentProvider : GroovyNamedArgumentProvider.EP_NAME.getExtensions()) {
      namedArgumentProvider.getNamedArguments(call, method, namedArguments);
    }

    return namedArguments;
  }

  protected static class StringTypeCondition implements Condition<PsiType> {
    private final String myTypeName;

    public StringTypeCondition(String typeName) {
      this.myTypeName = typeName;
    }

    @Override
    public boolean value(PsiType psiType) {
      return InheritanceUtil.isInheritor(psiType, myTypeName);
    }
  }

  protected static class StringArrayTypeCondition implements Condition<PsiType> {
    private final String[] myTypeNames;

    public StringArrayTypeCondition(String ... typeNames) {
      this.myTypeNames = typeNames;
    }

    @Override
    public boolean value(PsiType psiType) {
      for (String typeName : myTypeNames) {
        if (InheritanceUtil.isInheritor(psiType, typeName)) {
          return true;
        }
      }

      return false;
    }
  }

  protected static class TypeCondition implements Condition<PsiType> {
    private final PsiType myType;
    private final GroovyPsiElement myContext;

    public TypeCondition(PsiType type, GroovyPsiElement context) {
      myType = type;
      myContext = context;
    }

    @Override
    public boolean value(PsiType psiType) {
      return TypesUtil.isAssignable(myType, psiType, myContext);
    }
  }
}
