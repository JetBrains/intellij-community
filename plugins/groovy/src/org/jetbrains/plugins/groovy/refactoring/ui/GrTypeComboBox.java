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
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.swing.*;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrTypeComboBox extends JComboBox {

  private static final Logger LOG = Logger.getInstance(GrTypeComboBox.class);

  public static GrTypeComboBox createTypeComboBoxWithDefType(@Nullable PsiType type) {
    return new GrTypeComboBox(type, null, true, null, null, false);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(GrExpression expression) {
    return createTypeComboBoxFromExpression(expression, false);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(GrExpression expression, boolean selectDef) {
    PsiType type = expression.getType();

    if (GroovyRefactoringUtil.isDiamondNewOperator(expression)) {
      LOG.assertTrue(expression instanceof GrNewExpression);
      PsiType expected = PsiImplUtil.inferExpectedTypeForDiamond(expression);
      return new GrTypeComboBox(type, expected, expected == null, expression.getManager(), expression.getResolveScope(), selectDef);
    }
    else {
      return new GrTypeComboBox(type, null, true, null, null, selectDef);
    }
  }

  /**
   * @param type
   * @param min
   * @param createDef
   * @param manager   - must not be null if min is not null
   * @param scope     - must not be null if min is not null
   */
  private GrTypeComboBox(@Nullable PsiType type,
                         @Nullable PsiType min,
                         boolean createDef,
                         @Nullable PsiManager manager,
                         @Nullable GlobalSearchScope scope,
                         boolean selectDef) {
    LOG.assertTrue(min == null || manager != null);
    LOG.assertTrue(min == null || scope != null);

    if (type instanceof PsiDisjunctionType) type = ((PsiDisjunctionType)type).getLeastUpperBound();


    Map<String, PsiType> types = Collections.emptyMap();
    if (type != null) {
      types = getCompatibleTypeNames(type, min, manager, scope);
    }

    if (createDef || types.isEmpty()) {
      addItem(new PsiTypeItem(null));
    }

    for (String typeName : types.keySet()) {
      addItem(new PsiTypeItem(types.get(typeName)));
    }

    if (!selectDef && createDef && getItemCount() > 1) {
      setSelectedIndex(1);
    }
  }

  @Nullable
  public PsiType getSelectedType() {
    final Object selected = getSelectedItem();
    assert selected instanceof PsiTypeItem;
    return ((PsiTypeItem)selected).getType();
  }


  private static Map<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type,
                                                             @Nullable PsiType min,
                                                             PsiManager manager,
                                                             GlobalSearchScope scope) {
    if (type instanceof PsiDisjunctionType) type = ((PsiDisjunctionType)type).getLeastUpperBound();


    // if initial type is not assignable to min type we don't take into consideration min type.
    if (min != null && !TypesUtil.isAssignable(min, type, manager, scope)) {
      min = null;
    }

    Map<String, PsiType> map = new LinkedHashMap<String, PsiType>();
    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;
    final Set<PsiType> set = new LinkedHashSet<PsiType>();
    set.add(type);
    while (!set.isEmpty()) {
      PsiType cur = set.iterator().next();
      set.remove(cur);
      if (!map.containsValue(cur) && (min == null || TypesUtil.isAssignable(min, cur, manager, scope))) {
        if (isPartiallySubstituted(cur)) {
          LOG.assertTrue(cur instanceof PsiClassType);
          PsiClassType rawType = ((PsiClassType)cur).rawType();
          map.put(rawType.getPresentableText(), rawType);
        }
        else {
          map.put(cur.getPresentableText(), cur);
        }
        for (PsiType superType : cur.getSuperTypes()) {
          if (!map.containsValue(superType)) {
            set.add(superType);
          }
        }
      }
    }
    return map;
  }

  private static boolean isPartiallySubstituted(PsiType type) {
    if (!(type instanceof PsiClassType)) return false;
    PsiType[] parameters = ((PsiClassType)type).getParameters();

    PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
    PsiClass clazz = classResolveResult.getElement();
    if (clazz == null) return false;

    return clazz.getTypeParameters().length != parameters.length;
  }

  private static class PsiTypeItem {
    @Nullable
    private final PsiType myType;

    private PsiTypeItem(final PsiType type) {
      myType = type;
    }

    @Nullable
    public PsiType getType() {
      return myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiTypeItem that = (PsiTypeItem)o;

      if (myType == null) {
        if (that.myType != null) return false;
      }
      else {
        if (!myType.equals(that.myType)) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return myType == null ? 0 : myType.hashCode();
    }

    @Override
    public String toString() {
      return myType == null ? "def" : myType.getPresentableText();
    }
  }
}
