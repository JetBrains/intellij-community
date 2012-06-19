package org.jetbrains.android.augment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
class AndroidLightField extends LightFieldBuilder {
  private final PsiClass myContext;
  private final PsiType myType;

  public AndroidLightField(@NotNull String name,
                           @NotNull PsiClass context,
                           @NotNull PsiType type) {
    super(name, type, context);
    myContext = context;
    myType = type;
    setContainingClass(context);
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
    return new AndroidLightField(name, myContext, myType);
  }
}
