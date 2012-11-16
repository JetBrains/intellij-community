package org.jetbrains.android.dom.converters;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class OnClickConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final int length = element.getTextLength();
    if (length > 1) {
      return new PsiReference[]{new MyReference((XmlAttributeValue)element, new TextRange(1, length - 1))};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  private static class MyReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> {

    public MyReference(XmlAttributeValue value, TextRange range) {
      super(value, range, true);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return ResolveCache.getInstance(myElement.getProject())
        .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<MyReference>() {
          @NotNull
          @Override
          public ResolveResult[] resolve(@NotNull MyReference myReference, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, incompleteCode);
    }

    @NotNull
    private ResolveResult[] resolveInner() {
      final String methodName = myElement.getValue();
      if (methodName == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);

      if (module == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final Project project = myElement.getProject();
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

      final PsiMethod[] methods = cache.getMethodsByName(methodName, module.getModuleWithDependenciesScope());
      if (methods.length == 0) {
        return ResolveResult.EMPTY_ARRAY;
      }

      final List<ResolveResult> result = new ArrayList<ResolveResult>();
      for (PsiMethod method : methods) {
        if (checkSignature(method)) {
          result.add(new PsiElementResolveResult(method));
        }
      }
      return result.toArray(new ResolveResult[result.size()]);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final Project project = myElement.getProject();
      final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);

      if (module == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final PsiClass activityClass = JavaPsiFacade.getInstance(project)
        .findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));
      if (activityClass == null) {
        return EMPTY_ARRAY;
      }

      final List<Object> result = new ArrayList<Object>();
      final Set<String> methodNames = new HashSet<String>();

      ClassInheritorsSearch.search(activityClass, module.getModuleWithDependenciesScope(), true).forEach(new Processor<PsiClass>() {
        public boolean process(PsiClass c) {
          for (PsiMethod method : c.getMethods()) {
            if (checkSignature(method) && methodNames.add(method.getName())) {
              result.add(createLookupElement(method));
            }
          }
          return true;
        }
      });
      return ArrayUtil.toObjectArray(result);
    }
  }

  public static boolean checkSignature(PsiMethod method) {
    if (method.getReturnType() != PsiType.VOID) {
      return false;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null || aClass.isInterface()) {
      return false;
    }

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }

    final PsiType paramType = parameters[0].getType();
    if (!(paramType instanceof PsiClassType)) {
      return false;
    }

    final PsiClass paramClass = ((PsiClassType)paramType).resolve();
    return paramClass != null && AndroidUtils.VIEW_CLASS_NAME.equals(paramClass.getQualifiedName());
  }

  private static LookupElement createLookupElement(PsiMethod method) {
    final LookupElementBuilder builder = LookupElementBuilder.create(method, method.getName())
      .withIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
      .withPresentableText(method.getName());
    final PsiClass containingClass = method.getContainingClass();
    return containingClass != null
           ? builder.withTailText(" (" + containingClass.getQualifiedName() + ')')
           : builder;
  }
}
