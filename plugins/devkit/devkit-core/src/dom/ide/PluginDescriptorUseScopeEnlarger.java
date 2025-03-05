// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastVisibility;

import java.util.Collection;

final class PluginDescriptorUseScopeEnlarger extends UseScopeEnlarger {

  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    Project project = element.getProject();

    if (element instanceof PomTargetPsiElement) {
      PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DomTarget) {
        DomElement domElement = ((DomTarget)target).getDomElement();
        if (domElement instanceof ExtensionPoint ||
            domElement instanceof Extension) {
          return getAllPluginDescriptorFilesSearchScope(project);
        }

        if (domElement instanceof ActionOrGroup) {

          // IJ Project: missing deps for ActionOrGroupIdReference in code
          if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
            return GlobalSearchScopesCore.projectProductionScope(project)
              .uniteWith(GlobalSearchScopesCore.projectTestScope(project))
              .uniteWith(getAllPluginDescriptorFilesSearchScope(project));
          }
          
          return getAllPluginDescriptorFilesSearchScope(project);
        }
      }
      return null;
    }

    if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      // we use UAST to properly handle both Java and Kotlin classes
      var uClass = UastContextKt.toUElement(element, UClass.class);

      if (uClass != null &&
          (uClass.getVisibility() == UastVisibility.PUBLIC ||
           uClass.getVisibility() == UastVisibility.PACKAGE_LOCAL)) {
        return getAllPluginDescriptorFilesSearchScope(project);
      }
    }

    return null;
  }

  private static @NotNull GlobalSearchScope getAllPluginDescriptorFilesSearchScope(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Collection<VirtualFile> pluginXmlFiles =
        DomService.getInstance().getDomFileCandidates(IdeaPlugin.class,
                                                      GlobalSearchScope.allScope(project));

      GlobalSearchScope scope = GlobalSearchScope.filesScope(project, pluginXmlFiles);
      return new CachedValueProvider.Result<>(scope, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
