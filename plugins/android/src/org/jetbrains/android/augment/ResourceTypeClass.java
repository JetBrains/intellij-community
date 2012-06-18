package org.jetbrains.android.augment;

import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
* @author Eugene.Kudelevsky
*/
class ResourceTypeClass extends AndroidLightClass {
  private CachedValue<PsiField[]> myFieldsCache;
  private final AndroidFacet myFacet;

  public ResourceTypeClass(@NotNull AndroidFacet facet, @NotNull String name, @NotNull PsiClass context) {
    super(context, name);
    myFacet = facet;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<PsiField[]>() {
        @Override
        public Result<PsiField[]> compute() {
          final PsiField[] fields = buildResourceFields(myFacet, myName, ResourceTypeClass.this);
          return Result.create(fields, PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    }
    return myFieldsCache.getValue();
  }

  @NotNull
  private static PsiField[] buildResourceFields(@NotNull AndroidFacet facet,
                                                @NotNull String resType,
                                                @NotNull final AndroidLightClass context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(facet.getModule().getProject());
    final Collection<String> resNames = facet.getLocalResourceManager().getResourceNames(resType);
    final PsiField[] result = new PsiField[resNames.size()];
    int i = 0;
    for (String resName : resNames) {
      final AndroidLightField field = new AndroidLightField(AndroidResourceUtil.getFieldNameByResourceName(resName), context, PsiType.INT);
      field.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
      field.setInitializer(factory.createExpressionFromText("0", field));
      result[i++] = field;
    }
    return result;
  }
}
