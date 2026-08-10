// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public final class GrTypeComboBox extends ComboBox {

  private static final Logger LOG = Logger.getInstance(GrTypeComboBox.class);

  public static GrTypeComboBox createTypeComboBoxWithDefType(@Nullable PsiType type, @NotNull PsiElement context) {
    return new GrTypeComboBox(type, null, true, context, GroovyApplicationSettings.Type.TYPED);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(@NotNull GrExpression expression) {
    return createTypeComboBoxFromExpression(expression, GroovyApplicationSettings.Type.TYPED);
  }

  public static GrTypeComboBox createTypeComboBoxFromExpression(@NotNull GrExpression expression, 
                                                                GroovyApplicationSettings.Type selectType) {
    PsiType type = expression.getType();
    if (expression instanceof GrReferenceExpression ref) {
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        type = TypesUtil.createJavaLangClassType(type, expression);
      }
    }
    if (GroovyRefactoringUtil.isDiamondNewOperator(expression)) {
      LOG.assertTrue(expression instanceof GrNewExpression);
      PsiType expected = PsiImplUtil.inferExpectedTypeForDiamond(expression);
      return new GrTypeComboBox(type, expected, expected == null, expression, selectType);
    }
    else {
      if (type == PsiTypes.nullType()) {
        type = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
      }
      return new GrTypeComboBox(type, null, true, expression, selectType);
    }
  }

  public static GrTypeComboBox createEmptyTypeComboBox() {
    return new GrTypeComboBox(null, null, false, null, GroovyApplicationSettings.Type.TYPED);
  }

  private GrTypeComboBox(@Nullable PsiType type,
                         @Nullable PsiType min,
                         boolean createDef,
                         @Nullable PsiElement context,
                         GroovyApplicationSettings.Type selectType) {
    LOG.assertTrue(min == null || context != null);
    LOG.assertTrue(type == null || context != null);

    if (type instanceof PsiDisjunctionType disjunction) type = disjunction.getLeastUpperBound();

    Map<String, PsiType> types = type != null ? getCompatibleTypeNames(type, min, context) : Collections.emptyMap();

    int count = 0;
    if (createDef || types.isEmpty()) {
      assert context != null;
      GroovyPsiManager manager = GroovyPsiManager.getInstance(context.getProject());
      GlobalSearchScope scope = context.getResolveScope();
      addItem(new PsiTypeItem(manager.createTypeByFQClassName(GrModifier.DEF, scope)));
      addItem(new PsiTypeItem(manager.createTypeByFQClassName(PsiModifier.FINAL, scope)));
      count += 2;
      if (GroovyConfigUtils.isAtLeastGroovy30(context)) {
        count++;
        addItem(new PsiTypeItem(manager.createTypeByFQClassName(GrModifier.VAR, scope)));
        if (GroovyConfigUtils.isAtLeastGroovy60(context)) {
          count++;
          addItem(new PsiTypeItem(manager.createTypeByFQClassName(GrModifier.VAL, scope)));
        }
      }
    }

    if (type != null && type.equalsToText(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL)) {
      //suggest double as the second item after original BigDecimal
      addItem(new PsiTypeItem(type));
      types.remove(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
      addItem(new PsiTypeItem(PsiTypes.doubleType()));
    }
    for (PsiType t : types.values()) {
      addItem(new PsiTypeItem(t));
    }

    if (createDef && getItemCount() > selectType.ordinal() && count >= selectType.ordinal()) {
      setSelectedIndex(selectType.ordinal());
    }
  }

  public void addClosureTypesFrom(@Nullable PsiType type, @NotNull PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiType cl = type == null || type == PsiTypes.nullType()
                       ? factory.createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, context)
                       : factory.createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE + '<' + type.getCanonicalText() + '>', context);
    addItem(new PsiTypeItem(cl, true));
  }

  public @NotNull PsiType getSelectedType() {
    final Object selected = getSelectedItem();
    assert selected instanceof PsiTypeItem;
    return ((PsiTypeItem)selected).getType();
  }

  public boolean isClosureSelected() {
    return ((PsiTypeItem)getSelectedItem()).isClosure();
  }


  private static Map<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type, @Nullable PsiType min, @NotNull PsiElement context) {
    if (type instanceof PsiDisjunctionType disjunction) type = disjunction.getLeastUpperBound();

    // if initial type is not assignable to min type we don't take into consideration min type.
    if (min != null && !TypesUtil.isAssignable(min, type, context)) {
      min = null;
    }

    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;
    final Set<PsiType> set = new LinkedHashSet<>();
    set.add(type);
    Map<String, PsiType> map = new LinkedHashMap<>();
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
    if (!(type instanceof PsiClassType classType)) return false;
    PsiType[] parameters = classType.getParameters();

    PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    PsiClass clazz = classResolveResult.getElement();
    return clazz != null && clazz.getTypeParameters().length != parameters.length;
  }

  public static void registerUpDownHint(JComponent component, GrTypeComboBox combo) {
    final AnAction arrow = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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
      if (!UISettings.getInstance().getCycleScrolling()) {
        return;
      }
      next = (next + size) % size;
    }
    combo.setSelectedIndex(next);
  }

  private static final class PsiTypeItem {
    private final @NotNull PsiType myType;
    private final boolean isClosure;

    private PsiTypeItem(@NotNull PsiType type) {
      this(type, false);
    }

    private PsiTypeItem(@NotNull PsiType type, boolean closure) {
      myType = type;
      isClosure = closure;
    }

    public @NotNull PsiType getType() {
      return myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiTypeItem that = (PsiTypeItem)o;
      return myType.equals(that.myType);
    }

    @Override
    public int hashCode() {
      return myType.hashCode();
    }

    @Override
    public @NlsSafe String toString() {
      return myType.getPresentableText();
    }

    public boolean isClosure() {
      return isClosure;
    }
  }
}
