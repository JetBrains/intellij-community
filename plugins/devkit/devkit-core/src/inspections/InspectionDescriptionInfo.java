// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastModificationTracker;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.inspections.quickfix.PluginDescriptorChooser;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.processExtensionDeclarations;

public final class InspectionDescriptionInfo {

  private final String myFilename;
  private final PsiMethod myShortNameMethod;
  private final PsiFile myDescriptionFile;
  private final boolean myShortNameInXml;

  private InspectionDescriptionInfo(@NotNull String filename, @Nullable PsiMethod shortNameMethod,
                                    @Nullable PsiFile descriptionFile, boolean shortNameInXml) {
    myFilename = filename;
    myShortNameMethod = shortNameMethod;
    myDescriptionFile = descriptionFile;
    myShortNameInXml = shortNameInXml;
  }

  public static InspectionDescriptionInfo create(@NotNull Module module, @NotNull PsiClass psiClass) {
    assert psiClass.getName() != null;

    PsiMethod getShortNameMethod = PsiUtil.findNearestMethod("getShortName", psiClass);
    if (getShortNameMethod != null &&
        Objects.requireNonNull(getShortNameMethod.getContainingClass()).hasModifierProperty(PsiModifier.ABSTRACT)) {
      getShortNameMethod = null;
    }

    boolean shortNameInXml;
    String filename = null;
    if (getShortNameMethod == null) {
      shortNameInXml = true;
      Extension extension = findExtension(psiClass);
      if (extension != null) {
        filename = extension.getXmlTag().getAttributeValue("shortName");
      }
    }
    else {
      shortNameInXml = false;
      filename = getReturnedLiteral(getShortNameMethod, psiClass);
    }

    if (filename == null) {
      filename = InspectionProfileEntry.getShortName(psiClass.getName());
    }

    PsiFile descriptionFile = resolveInspectionDescriptionFile(module, filename);
    return new InspectionDescriptionInfo(filename, getShortNameMethod, descriptionFile, shortNameInXml);
  }

  @Nullable
  public static Extension findExtension(PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
      Extension extension = module == null ? null : doFindExtension(module, psiClass);
      return CachedValueProvider.Result.create(extension,
                                               UastModificationTracker.getInstance(psiClass.getProject()),
                                               psiClass.getManager().getModificationTracker().forLanguage(XMLLanguage.INSTANCE));
    });
  }

  @Nullable
  private static Extension doFindExtension(Module module, PsiClass psiClass) {
    // Try search in narrow scopes first
    Project project = module.getProject();
    Set<DomFileElement<IdeaPlugin>> processedFileElements = new HashSet<>();
    for (GlobalSearchScope scope : DescriptionCheckerUtil.searchScopes(module)) {
      List<DomFileElement<IdeaPlugin>> fileElements = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
      fileElements.removeAll(processedFileElements);
      List<DomFileElement<IdeaPlugin>> filteredFileElements =
        PluginDescriptorChooser.findAppropriateIntelliJModule(module.getName(), fileElements);
      SearchScope searchScope = new LocalSearchScope(filteredFileElements.stream().map(DomFileElement::getFile).toArray(PsiElement[]::new));

      Ref<Extension> result = Ref.create();
      processExtensionDeclarations(Objects.requireNonNull(psiClass.getQualifiedName()), module.getProject(),
                                   true, searchScope, (extension, tag) -> {
          ExtensionPoint extensionPoint = extension.getExtensionPoint();
          if (extensionPoint != null &&
              InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), InspectionEP.class.getName())) {
            result.set(extension);
            return false;
          }
          return true;
        });
      Extension extension = result.get();
      if (extension != null) return extension;

      processedFileElements.addAll(fileElements);
    }
    return null;
  }

  @Nullable
  private static PsiFile resolveInspectionDescriptionFile(Module module, @Nullable String filename) {
    if (filename == null) return null;

    String nameWithSuffix = filename + ".html";
    return DescriptionCheckerUtil.allDescriptionDirs(module, DescriptionType.INSPECTION)
      .map(directory -> directory.findFile(nameWithSuffix))
      .map(directory -> directory != null && directory.getName().equals(nameWithSuffix) ? directory : null)
      .nonNull().findFirst().orElse(null);
  }

  @NotNull
  public String getFilename() {
    return myFilename;
  }

  @Nullable
  public PsiMethod getShortNameMethod() {
    return myShortNameMethod;
  }

  @Nullable
  public PsiFile getDescriptionFile() {
    return myDescriptionFile;
  }

  public boolean isShortNameInXml() {
    return myShortNameInXml;
  }

  @Nullable
  private static String getReturnedLiteral(PsiMethod method, PsiClass cls) {
    final UExpression expression = PsiUtil.getReturnedExpression(method);
    if (expression == null) return null;

    if (expression instanceof UReferenceExpression) {
      final String methodName = ((UReferenceExpression)expression).getResolvedName();
      if ("getSimpleName".equals(methodName)) {
        return cls.getName();
      }
    }

    return UastUtils.evaluateString(expression);
  }
}
