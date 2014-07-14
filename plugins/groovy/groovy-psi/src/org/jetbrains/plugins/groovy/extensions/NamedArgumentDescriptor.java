/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
* @author Sergey Evdokimov
*/
public class NamedArgumentDescriptor {

  public static final NamedArgumentDescriptor SIMPLE_ON_TOP = new UnmodifiableDescriptor(Priority.ALWAYS_ON_TOP);
  public static final NamedArgumentDescriptor SIMPLE_AS_LOCAL_VAR = new UnmodifiableDescriptor(Priority.AS_LOCAL_VARIABLE);
  public static final NamedArgumentDescriptor SIMPLE_NORMAL = new UnmodifiableDescriptor(Priority.NORMAL);
  public static final NamedArgumentDescriptor SIMPLE_UNLIKELY = new UnmodifiableDescriptor(Priority.UNLIKELY);

  public static final StringTypeConditionWithPriority TYPE_STRING = new StringTypeConditionWithPriority(CommonClassNames.JAVA_LANG_STRING);
  public static final StringTypeConditionWithPriority TYPE_CLOSURE = new StringTypeConditionWithPriority(GroovyCommonClassNames.GROOVY_LANG_CLOSURE);
  public static final StringTypeConditionWithPriority TYPE_MAP = new StringTypeConditionWithPriority(CommonClassNames.JAVA_UTIL_MAP);
  public static final StringTypeConditionWithPriority TYPE_LIST = new StringTypeConditionWithPriority(CommonClassNames.JAVA_UTIL_LIST);
  public static final StringTypeConditionWithPriority TYPE_BOOL = new StringTypeConditionWithPriority(CommonClassNames.JAVA_LANG_BOOLEAN);
  public static final StringTypeConditionWithPriority TYPE_CLASS = new StringTypeConditionWithPriority(CommonClassNames.JAVA_LANG_CLASS);
  public static final StringTypeConditionWithPriority TYPE_INTEGER = new StringTypeConditionWithPriority(CommonClassNames.JAVA_LANG_INTEGER);

  private final PsiElement myNavigationElement;
  private final PsiSubstitutor mySubstitutor;

  private Priority myPriority = Priority.ALWAYS_ON_TOP;

  public NamedArgumentDescriptor() {
    this(null);
  }

  public NamedArgumentDescriptor(@Nullable PsiElement navigationElement) {
    this(navigationElement, PsiSubstitutor.EMPTY);
  }

  public NamedArgumentDescriptor(@Nullable PsiElement navigationElement, PsiSubstitutor substitutor) {
    myNavigationElement = navigationElement;
    mySubstitutor = substitutor;
  }

  @NotNull
  public Priority getPriority() {
    return myPriority;
  }

  public NamedArgumentDescriptor setPriority(@NotNull Priority priority) {
    myPriority = priority;
    return this;
  }

  public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
    return true;
  }

  @Nullable
  public PsiPolyVariantReference createReference(@NotNull GrArgumentLabel element) {
    final PsiElement navigationElement = getNavigationElement();
    if (navigationElement == null) return null;

    return new NamedArgumentReference(element, navigationElement, mySubstitutor);
  }

  @Nullable
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public static class NamedArgumentReference extends PsiPolyVariantReferenceBase<GrArgumentLabel> {
    private final PsiElement myNavigationElement;
    private final PsiSubstitutor mySubstitutor;

    public NamedArgumentReference(GrArgumentLabel element, @NotNull PsiElement navigationElement, PsiSubstitutor substitutor) {
      super(element);
      myNavigationElement = navigationElement;
      mySubstitutor = substitutor;
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
    public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
      return new GroovyResolveResult[]{new GroovyResolveResultImpl(myNavigationElement, null, null, mySubstitutor, true, true)};
    }
  }

  public enum Priority {
    ALWAYS_ON_TOP,
    AS_LOCAL_VARIABLE,
    NORMAL,
    UNLIKELY
  }

  private static class StringTypeConditionWithPriority extends StringTypeCondition {

    private final StringTypeConditionWithPriority[] myInstances;

    public StringTypeConditionWithPriority(String typeName) {
      this(typeName, Priority.ALWAYS_ON_TOP, new StringTypeConditionWithPriority[Priority.values().length]);
    }

    private StringTypeConditionWithPriority(String typeName, Priority priority, StringTypeConditionWithPriority[] instances) {
      super(typeName);
      myInstances = instances;
      super.setPriority(priority);
      instances[priority.ordinal()] = this;
    }

    public StringTypeConditionWithPriority withPriority(Priority priority) {
      StringTypeConditionWithPriority res = myInstances[priority.ordinal()];
      if (res == null) {
        res = new StringTypeConditionWithPriority(myTypeName, priority, myInstances);
      }

      return res;
    }

    @Override
    public NamedArgumentDescriptor setPriority(@NotNull Priority priority) {
      throw new UnsupportedOperationException("Use withPriority(priority)");
    }
  }

  public static class StringTypeCondition extends NamedArgumentDescriptor {
    protected final String myTypeName;

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

  public static class StringArrayTypeCondition extends NamedArgumentDescriptor {
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

  public static class TypeCondition extends NamedArgumentDescriptor {
    private final PsiType myType;

    public TypeCondition(@NotNull PsiType type) {
      this(type, null, PsiSubstitutor.EMPTY);
    }

    public TypeCondition(@NotNull PsiType type, @Nullable PsiElement navigationElement) {
      this(type, navigationElement, PsiSubstitutor.EMPTY);
    }

    public TypeCondition(PsiType type, PsiElement navigationElement, PsiSubstitutor substitutor) {
      super(navigationElement, substitutor);
      myType = type;
    }

    @Override
    public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
      return TypesUtil.isAssignable(myType, type, context);
    }
  }

  private static class UnmodifiableDescriptor extends NamedArgumentDescriptor {
    public UnmodifiableDescriptor(Priority priority) {
      super.setPriority(priority);
    }

    @Override
    public NamedArgumentDescriptor setPriority(@NotNull Priority priority) {
      throw new UnsupportedOperationException();
    }
  }

}
