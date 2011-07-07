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
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public abstract class GroovyNamedArgumentProvider {

  public static final ExtensionPointName<GroovyNamedArgumentProvider> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.namedArgumentProvider");

  public static final StringTypeCondition TYPE_STRING = new StringTypeCondition(CommonClassNames.JAVA_LANG_STRING);
  public static final StringTypeCondition TYPE_MAP = new StringTypeCondition(CommonClassNames.JAVA_UTIL_MAP);
  public static final StringTypeCondition TYPE_BOOL = new StringTypeCondition(CommonClassNames.JAVA_LANG_BOOLEAN);
  public static final StringTypeCondition TYPE_CLASS = new StringTypeCondition(CommonClassNames.JAVA_LANG_CLASS);
  public static final StringTypeCondition TYPE_INTEGER = new StringTypeCondition(CommonClassNames.JAVA_LANG_INTEGER);
  public static final ArgumentDescriptor TYPE_ANY = new ArgumentDescriptor();
  public static final ArgumentDescriptor TYPE_ANY_NOT_FIRST = new ArgumentDescriptor().setShowFirst(false);

  public abstract void getNamedArguments(@NotNull GrCall call,
                                         @Nullable PsiElement resolve,
                                         @Nullable String argumentName,
                                         boolean forCompletion,
                                         Map<String, ArgumentDescriptor> result);

  public static Map<String, ArgumentDescriptor> getNamedArgumentsFromAllProviders(@NotNull GrCall call,
                                                                                  @Nullable String argumentName,
                                                                                  boolean forCompletion) {
    Map<String, ArgumentDescriptor> namedArguments = new HashMap<String, ArgumentDescriptor>() {
      @Override
      public ArgumentDescriptor put(String key, ArgumentDescriptor value) {
        ArgumentDescriptor oldValue = super.put(key, value);
        if (oldValue != null) {
          super.put(key, oldValue);
        }

        return oldValue;
      }
    };

    GroovyResolveResult[] callVariants = call.getCallVariants(null);

    if (callVariants.length == 0) {
      for (GroovyNamedArgumentProvider namedArgumentProvider : GroovyNamedArgumentProvider.EP_NAME.getExtensions()) {
        namedArgumentProvider.getNamedArguments(call, null, argumentName, forCompletion, namedArguments);
      }
    }
    else {
      for (GroovyResolveResult result : callVariants) {
        PsiElement element = result.getElement();
        if (element instanceof GrAccessorMethod) continue;

        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (!(parameters.length == 0 ? method.isConstructor() : canBeMap(parameters[0]))) {
            continue;
          }

          collectVariantsFromSimpleDescriptors(namedArguments, method);
        }

        for (GroovyNamedArgumentProvider namedArgumentProvider : GroovyNamedArgumentProvider.EP_NAME.getExtensions()) {
          namedArgumentProvider.getNamedArguments(call, element, argumentName, forCompletion, namedArguments);
        }
      }
    }

    return namedArguments;
  }

  private static void collectVariantsFromSimpleDescriptors(Map<String, ArgumentDescriptor> res, PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null) {
      Map<String, ArgumentDescriptor> map =
        SimpleGroovyNamedArgumentProvider.getMethodMap(containingClass.getQualifiedName(), method.getName());
      if (map != null) {
        res.putAll(map);
      }
    }
  }

  public static boolean canBeMap(PsiParameter parameter) {
    if (parameter instanceof GrParameter) {
      if (((GrParameter)parameter).getTypeElementGroovy() == null) return true;
    }
    return GroovyPsiManager.isInheritorCached(parameter.getType(), CommonClassNames.JAVA_UTIL_MAP);
  }

  public static class ArgumentDescriptor {

    private final PsiElement myNavigationElement;

    private boolean isShowFirst = true;

    public ArgumentDescriptor() {
      this(null);
    }

    public ArgumentDescriptor(@Nullable PsiElement navigationElement) {
      this.myNavigationElement = navigationElement;
    }

    public boolean isShowFirst() {
      return isShowFirst;
    }

    public ArgumentDescriptor setShowFirst(boolean showFirst) {
      isShowFirst = showFirst;
      return this;
    }

    public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
      return true;
    }

    @Nullable
    public PsiPolyVariantReference createReference(@NotNull GrArgumentLabel element) {
      final PsiElement navigationElement = getNavigationElement();
      if (navigationElement == null) return null;

      return new NamedArgumentReference(element, navigationElement);
    }

    @Nullable
    public PsiElement getNavigationElement() {
      return myNavigationElement;
    }

    public static class NamedArgumentReference extends PsiPolyVariantReferenceBase<GrArgumentLabel> {
      private final PsiElement myNavigationElement;

      public NamedArgumentReference(GrArgumentLabel element, @NotNull PsiElement navigationElement) {
        super(element);
        myNavigationElement = navigationElement;
      }

      @Override
      public PsiElement resolve() {
        return myNavigationElement;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (element == myNavigationElement) return getElement();
        return super.bindToElement(element);
      }

      @Override
      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        final PsiElement resolved = resolve();

        if (resolved instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod) resolved;
          final String oldName = getElement().getName();
          if (!method.getName().equals(oldName)) { //was property reference to accessor
            if (PropertyUtil.isSimplePropertySetter(method)) {
              final String newPropertyName = PropertyUtil.getPropertyName(newElementName);
              if (newPropertyName != null) {
                newElementName = newPropertyName;
              }
            }
          }
        }

        return super.handleElementRename(newElementName);
      }

      @NotNull
      @Override
      public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      @NotNull
      @Override
      public ResolveResult[] multiResolve(boolean incompleteCode) {
        return new ResolveResult[]{new GroovyResolveResultImpl(myNavigationElement, true)};
      }
    }
  }

  protected static class StringTypeCondition extends ArgumentDescriptor {
    private final String myTypeName;

    public StringTypeCondition(String typeName) {
      this(typeName, null);
    }

    public StringTypeCondition(String typeName, @Nullable PsiElement navigationElement) {
      super(navigationElement);
      myTypeName = typeName;
    }

    @Override
    public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
      return GroovyPsiManager.isInheritorCached(type, myTypeName);
    }
  }

  protected static class StringArrayTypeCondition extends ArgumentDescriptor {
    private final String[] myTypeNames;

    public StringArrayTypeCondition(String... typeNames) {
      this(null, typeNames);
    }

    public StringArrayTypeCondition(@Nullable PsiElement navigationElement, String... typeNames) {
      super(navigationElement);
      this.myTypeNames = typeNames;
    }

    @Override
    public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
      for (String typeName : myTypeNames) {
        if (GroovyPsiManager.isInheritorCached(type, typeName)) {
          return true;
        }
      }
      return false;
    }
  }

  protected static class TypeCondition extends ArgumentDescriptor {
    private final PsiType myType;

    public TypeCondition(PsiType type, PsiElement navigationElement) {
      super(navigationElement);
      myType = type;
    }

    @Override
    public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
      return TypesUtil.isAssignable(myType, type, context);
    }
  }
}
