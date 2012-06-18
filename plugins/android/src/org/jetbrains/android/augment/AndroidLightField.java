package org.jetbrains.android.augment;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
class AndroidLightField extends LightFieldBuilder {
  private final AndroidLightClass myContext;

  public AndroidLightField(@NotNull String name,
                           @NotNull AndroidLightClass context,
                           @NotNull PsiType type) {
    super(name, type, context);
    myContext = context;
    setContainingClass(context);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myContext.getContainingFile();
  }
}
