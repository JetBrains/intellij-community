package org.jetbrains.android.dom.resources;

import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class DeclareStyleableNameConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final Module module = context.getModule();
    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        return new PsiReference[]{new MyReference(facet, value)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static class MyReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final GenericDomValue<String> myValue;
    private final AndroidFacet myFacet;

    public MyReference(@NotNull AndroidFacet facet, @NotNull GenericDomValue<String> value) {
      super(DomUtil.getValueElement(value), true);
      myFacet = facet;
      myValue = value;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return ResolveCache.getInstance(myElement.getProject())
        .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<MyReference>() {
          @NotNull
          @Override
          public ResolveResult[] resolve(@NotNull MyReference reference, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, incompleteCode);
    }

    private ResolveResult[] resolveInner() {
      final String value = myValue.getStringValue();

      if (value == null || value.length() <= 0) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final PsiClass[] classes = PsiShortNamesCache.getInstance(myElement.getProject())
        .getClassesByName(value, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      final ResolveResult[] result = new ResolveResult[classes.length];

      for (int i = 0; i < result.length; i++) {
        result[i] = new PsiElementResolveResult(classes[i]);
      }
      return result;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final PsiClass viewClass = JavaPsiFacade.getInstance(myElement.getProject())
        .findClass(AndroidUtils.VIEW_CLASS_NAME, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (viewClass == null) {
        return EMPTY_ARRAY;
      }
      final Set<Object> shortNames = new HashSet<Object>();

      ClassInheritorsSearch.search(viewClass, myFacet.getModule().getModuleWithDependenciesScope(), true).
        forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass aClass) {
            final String name = aClass.getName();

            if (name != null) {
              shortNames.add(JavaLookupElementBuilder.forClass(aClass, name, true));
            }
            return true;
          }
        });
      return shortNames.toArray();
    }
  }
}
