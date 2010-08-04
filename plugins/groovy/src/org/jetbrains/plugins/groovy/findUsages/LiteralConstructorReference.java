package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public class LiteralConstructorReference extends PsiReferenceBase.Poly<GrListOrMap> {
  private final PsiClassType myConstructedClass;

  public LiteralConstructorReference(@NotNull GrListOrMap element, @NotNull PsiClassType constructedClassType) {
    super(element, TextRange.from(0, 1), false);
    myConstructedClass = constructedClassType;
  }

  @Nullable
  private PsiType[] argTypes() {
    final GrListOrMap literal = getElement();
    final PsiType listType = literal.getType();
    if (listType instanceof GrTupleType) {
      return ((GrTupleType)listType).getComponentTypes();
    }
    else if (listType instanceof GrMapType && ((GrMapType)listType).getValueType("super") == null) {
      return PsiType.EMPTY_ARRAY;
    }
    return null;
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiType[] psiTypes = argTypes();
    if (psiTypes == null) return ResolveResult.EMPTY_ARRAY;

    return PsiUtil.getConstructorCandidates(myConstructedClass, psiTypes, getElement());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
