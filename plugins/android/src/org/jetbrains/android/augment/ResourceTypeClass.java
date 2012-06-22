package org.jetbrains.android.augment;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
    final LocalResourceManager manager = facet.getLocalResourceManager();
    final Map<String, PsiType> fieldNames = new HashMap<String, PsiType>();
    final boolean styleable = ResourceType.STYLEABLE.getName().equals(resClassName);
    final PsiType basicType = styleable ? PsiType.INT.createArrayType() : PsiType.INT;

    for (String resName : manager.getResourceNames(resClassName)) {
      fieldNames.put(resName, basicType);
    }

    if (styleable) {
      for (ResourceEntry entry : manager.getValueResourceEntries(ResourceType.ATTR.getName())) {
        final String resName = entry.getName();
        final String resContext = entry.getContext();

        if (resContext.length() > 0) {
          fieldNames.put(resContext + '_' + resName, PsiType.INT);
        }
      }
    }
    final PsiField[] result = new PsiField[fieldNames.size()];
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(facet.getModule().getProject());
    final Module circularDepLibWithSamePackage = AndroidCompileUtil.findCircularDependencyOnLibraryWithSamePackage(facet);
    final boolean generateNonFinalFields = facet.getConfiguration().LIBRARY_PROJECT || circularDepLibWithSamePackage != null;

    int idIterator = ResourceType.getEnum(resClassName).ordinal() * 100000;
    int i = 0;

    for (Map.Entry<String, PsiType> entry : fieldNames.entrySet()) {
      final String fieldName = AndroidResourceUtil.getFieldNameByResourceName(entry.getKey());
      final PsiType type = entry.getValue();
      final int id = -(idIterator++);
      final AndroidLightField field =
        new AndroidLightField(fieldName, context, type, !generateNonFinalFields, generateNonFinalFields ? null : id);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(id), field));
      result[i++] = field;
    }
    return result;
  }
}
