package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class FixedValuesReferenceProvider extends PsiReferenceProvider {

  private final String[] myValues;

  private boolean mySoft;

  public FixedValuesReferenceProvider(@NotNull String[] values) {
    this(values, false);
  }

  public FixedValuesReferenceProvider(@NotNull String[] values, boolean soft) {
    myValues = values;
    mySoft = soft;
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[]{
      new PsiReferenceBase<PsiElement>(element, mySoft) {
        @Nullable
        @Override
        public PsiElement resolve() {
          return null;
        }

        @NotNull
        @Override
        public Object[] getVariants() {
          return myValues;
        }
      }
    };
  }
}
