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
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrTypeComboBox extends ComboBox {

  private static final Logger LOG = Logger.getInstance(GrTypeComboBox.class);


  public static GrTypeComboBox createTypeComboBoxWithDefType(@Nullable PsiType type, @NotNull PsiElement context) {
    return new GrTypeComboBox(type, null, true, context, false);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(@NotNull GrExpression expression) {
    return createTypeComboBoxFromExpression(expression, false);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(@NotNull GrExpression expression, boolean selectDef) {
    PsiType type = expression.getType();
    if (expression instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)expression).resolve();
      if (resolved instanceof PsiClass) {
        type = TypesUtil.createJavaLangClassType(type, expression.getProject(), expression.getResolveScope());
      }
    }
    if (GroovyRefactoringUtil.isDiamondNewOperator(expression)) {
      LOG.assertTrue(expression instanceof GrNewExpression);
      PsiType expected = PsiImplUtil.inferExpectedTypeForDiamond(expression);
      return new GrTypeComboBox(type, expected, expected == null, expression, selectDef);
    }
    else {
      if (type == PsiType.NULL) {
        type = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
      }
      return new GrTypeComboBox(type, null, true, expression, selectDef);
    }
  }

  public static GrTypeComboBox createEmptyTypeComboBox() {
    return new GrTypeComboBox(null, null, false, null, false);
  }

  private GrTypeComboBox(@Nullable PsiType type,
                         @Nullable PsiType min,
                         boolean createDef,
                         @Nullable PsiElement context,
                         boolean selectDef) {
    LOG.assertTrue(min == null || context != null);
    LOG.assertTrue(type == null || context != null);

    if (type instanceof PsiDisjunctionType) type = ((PsiDisjunctionType)type).getLeastUpperBound();

    Map<String, PsiType> types = Collections.emptyMap();
    if (type != null) {
      types = getCompatibleTypeNames(type, min, context);
    }

    if (createDef || types.isEmpty()) {
      addItem(new PsiTypeItem(null));
    }

    if (type != null && type.equalsToText(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL)) {
      //suggest double as the second item after original BigDecimal
      addItem(new PsiTypeItem(type));
      types.remove(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
      addItem(new PsiTypeItem(PsiType.DOUBLE));
    }

    for (String typeName : types.keySet()) {
      addItem(new PsiTypeItem(types.get(typeName)));
    }

    if (!selectDef && createDef && getItemCount() > 1) {
      setSelectedIndex(1);
    }
  }

  public void addClosureTypesFrom(@Nullable PsiType type, @NotNull PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiType cl;
    if (type == null || type == PsiType.NULL) {
      cl = factory.createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, context);
    }
    else {
      cl = factory.createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE + '<' + type.getCanonicalText() + '>', context);
    }
    addItem(new PsiTypeItem(cl, true));
  }

  @Nullable
  public PsiType getSelectedType() {
    final Object selected = getSelectedItem();
    assert selected instanceof PsiTypeItem;
    return ((PsiTypeItem)selected).getType();
  }

  public boolean isClosureSelected() {
    return ((PsiTypeItem)getSelectedItem()).isClosure();
  }


  private static Map<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type,
                                                             @Nullable PsiType min,
                                                             @NotNull PsiElement context) {
    if (type instanceof PsiDisjunctionType) type = ((PsiDisjunctionType)type).getLeastUpperBound();


    // if initial type is not assignable to min type we don't take into consideration min type.
    if (min != null && !TypesUtil.isAssignable(min, type, context)) {
      min = null;
    }

    Map<String, PsiType> map = new LinkedHashMap<>();
    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;
    final Set<PsiType> set = new LinkedHashSet<>();
    set.add(type);
    while (!set.isEmpty()) {
      PsiType cur = set.iterator().next();
      set.remove(cur);
      if (!map.containsValue(cur) && (min == null || TypesUtil.isAssignable(min, cur, context))) {
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

  public static void registerUpDownHint(JComponent component, final GrTypeComboBox combo) {
    final AnAction arrow = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (e.getInputEvent() instanceof KeyEvent) {
          final int code = ((KeyEvent)e.getInputEvent()).getKeyCode();
          scrollBy(code == KeyEvent.VK_DOWN ? 1 : code == KeyEvent.VK_UP ? -1 : 0, combo);
        }
      }
    };
    final KeyboardShortcut up = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), null);
    final KeyboardShortcut down = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), null);
    arrow.registerCustomShortcutSet(new CustomShortcutSet(up, down), component);
  }

  private static void scrollBy(int delta, GrTypeComboBox combo) {
    if (delta == 0) return;
    final int size = combo.getModel().getSize();
    int next = combo.getSelectedIndex() + delta;
    if (next < 0 || next >= size) {
      if (!UISettings.getInstance().CYCLE_SCROLLING) {
        return;
      }
      next = (next + size) % size;
    }
    combo.setSelectedIndex(next);
  }

  private static class PsiTypeItem {
    @Nullable
    private final PsiType myType;

    private final boolean isClosure;

    private PsiTypeItem(final PsiType type) {
      this(type, false);
    }

    private PsiTypeItem(final PsiType type, boolean closure) {
      myType = type;
      isClosure = closure;
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

    public boolean isClosure() {
      return isClosure;
    }
  }
}
