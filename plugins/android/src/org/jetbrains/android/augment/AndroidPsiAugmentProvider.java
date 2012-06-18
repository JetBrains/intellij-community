package org.jetbrains.android.augment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.augment.PsiAugmentProvider;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPsiAugmentProvider extends PsiAugmentProvider {
  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    if (type != PsiClass.class ||
        !(element instanceof PsiClass)) {
      return Collections.emptyList();
    }
    final PsiClass aClass = (PsiClass)element;
    final String className = aClass.getName();

    if (!AndroidUtils.R_CLASS_NAME.equals(className) &&
        !AndroidUtils.MANIFEST_CLASS_NAME.equals(className)) {
      return Collections.emptyList();
    }

    final AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) {
      return Collections.emptyList();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return Collections.emptyList();
    }

    if (AndroidResourceUtil.isRJavaFile(facet, containingFile)) {
      final Set<String> types = ResourceReferenceConverter.getResourceTypesInCurrentModule(facet);
      final List<Psi> result = new ArrayList<Psi>(types.size());

      for (String resType : types) {
        final AndroidLightClass resClass = new ResourceTypeClass(facet, resType, aClass);
        result.add((Psi)resClass);
      }
      return result;
    }
    else if (AndroidResourceUtil.isManifestJavaFile(facet, containingFile)) {
      return Arrays.asList((Psi)new PermissionClass(facet, aClass),
                           (Psi)new PermissionGroupClass(facet, aClass));
    }
    return Collections.emptyList();
  }
}
