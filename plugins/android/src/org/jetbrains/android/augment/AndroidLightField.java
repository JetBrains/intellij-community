package org.jetbrains.android.augment;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
* @author Eugene.Kudelevsky
*/
class AndroidLightField extends LightFieldBuilder implements PsiVariableEx, SyntheticElement {
  private final PsiClass myContext;
  private final PsiType myType;
  private final Object myConstantValue;
  private final boolean myFinal;

  public AndroidLightField(@NotNull String name,
                           @NotNull PsiClass context,
                           @NotNull PsiType type,
                           boolean isFinal,
                           @Nullable Object constantValue) {
    super(name, type, context);
    myContext = context;
    myType = type;
    myConstantValue = constantValue;
    myFinal = isFinal;
    setContainingClass(context);

    final List<String> modifiers = new ArrayList<String>();
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.STATIC);

    if (isFinal) {
      modifiers.add(PsiModifier.FINAL);
    }
    setModifiers(ArrayUtil.toStringArray(modifiers));
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myContext.getContainingFile();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final AndroidLightField field = new AndroidLightField(name, myContext, myType, myFinal, myConstantValue);
    field.setInitializer(getInitializer());
    return field;
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    return computeConstantValue();
  }

  @Override
  public Object computeConstantValue() {
    return myConstantValue;
  }
}
