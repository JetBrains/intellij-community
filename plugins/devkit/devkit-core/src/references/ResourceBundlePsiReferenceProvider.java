// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.search.GlobalSearchScopesCore.projectProductionScope;

class ResourceBundlePsiReferenceProvider extends PsiReferenceProvider {

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof PsiFile && PropertiesImplUtil.isPropertiesFile((PsiFile)target);
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull ProcessingContext context) {
    return new PsiReference[]{new MyResourceBundleReference(element)};
  }

  private static final class MyResourceBundleReference extends ResourceBundleReference implements EmptyResolveMessageProvider {

    private MyResourceBundleReference(PsiElement element) {
      super(element, false);
    }

    /**
     * Resolves through one cached map of all bundles of the project: the base class scans every properties file per reference,
     * and a descriptor with hundreds of {@code bundle} attributes repeats that scan hundreds of times.
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      if (ModuleUtilCore.findModuleForPsiElement(myElement) == null) return super.multiResolve(incompleteCode);
      // bundle name can be used in dependency module
      List<PropertiesFile> files =
        PropertiesReferenceManager.getInstance(myElement.getProject()).findProductionPropertiesFiles(getCanonicalText());
      return PsiElementResolveResult.createResults(ContainerUtil.map(files, PropertiesFile::getContainingFile));
    }

    @Override
    public Object @NotNull [] getVariants() {
      final Project project = myElement.getProject();
      PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(project);
      final List<LookupElement> variants = new ArrayList<>();
      referenceManager.processPropertiesFiles(projectProductionScope(project), (baseName, propertiesFile) -> {
        final Icon icon = propertiesFile.getContainingFile().getIcon(Iconable.ICON_FLAG_READ_STATUS);
        final String relativePath = ProjectUtil.calcRelativeToProjectPath(propertiesFile.getVirtualFile(), project);
        variants.add(LookupElementBuilder.create(propertiesFile, baseName)
                       .withIcon(icon)
                       .withTailText(" (" + relativePath + ")", true));
        return true;
      }, this);
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }

    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("plugin.xml.convert.property.bundle.cannot.resolve");
    }
  }
}
