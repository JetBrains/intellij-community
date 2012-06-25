package org.jetbrains.android.inspections;

import com.android.resources.ResourceType;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiReferenceExpression> {
  @Override
  public void registerFixes(PsiReferenceExpression exp, QuickFixActionRegistrar registrar) {
    final Module contextModule = ModuleUtil.findModuleForPsiElement(exp);
    if (contextModule == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(contextModule);
    if (facet == null) {
      return;
    }

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final String aPackage = manifest.getPackage().getValue();
    if (aPackage == null) {
      return;
    }

    final PsiFile contextFile = exp.getContainingFile();
    if (contextFile == null) {
      return;
    }

    Pair<String, String> pair = AndroidResourceUtil.getReferredResourceField(facet, exp);
    if (pair == null) {
      final PsiElement parent = exp.getParent();
      if (parent instanceof PsiReferenceExpression) {
        pair = AndroidResourceUtil.getReferredResourceField(facet, (PsiReferenceExpression)parent);
      }
    }
    if (pair == null) {
      return;
    }
    final String resClassName = pair.getFirst();
    final String resFieldName = pair.getSecond();

    ResourceType resourceType = ResourceType.getEnum(resClassName);

    if (AndroidResourceUtil.ALL_VALUE_RESOURCE_TYPES.contains(resourceType)) {
      registrar
        .register(new CreateValueResourceQuickFix(facet, resourceType, resFieldName, contextFile, true));
    }
    if (AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.contains(resourceType)) {
      registrar.register(new CreateFileResourceQuickFix(facet, resourceType, resFieldName, contextFile, true));
    }
  }

  @NotNull
  @Override
  public Class<PsiReferenceExpression> getReferenceClass() {
    return PsiReferenceExpression.class;
  }
}
