package org.jetbrains.android.augment;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.compiler.AndroidCompileUtil;
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
  static PsiField[] buildResourceFields(@NotNull AndroidFacet facet,
                                        @NotNull String resClassName,
                                        @NotNull final PsiClass context) {
    final Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    final boolean generateNonFinalFields = facet.getConfiguration().LIBRARY_PROJECT || circularDepLibWithSamePackage != null;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(facet.getModule().getProject());
    final Collection<String> resNames = facet.getLocalResourceManager().getResourceNames(resClassName);
    final PsiField[] result = new PsiField[resNames.size()];
    int i = 0;
    for (String resName : resNames) {
      final PsiType type = ResourceType.STYLEABLE.getName().equals(resClassName)
                           ? PsiType.INT.createArrayType()
                           : PsiType.INT;
      final AndroidLightField field = new AndroidLightField(AndroidResourceUtil.getFieldNameByResourceName(resName), context,
                                                            type, !generateNonFinalFields, generateNonFinalFields ? null : 0);
      field.setInitializer(factory.createExpressionFromText("0", field));
      result[i++] = field;
    }
    return result;
  }
}
