// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;

public class PluginDescriptorUseScopeEnlarger extends UseScopeEnlarger {

  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    if (element instanceof PomTargetPsiElement) {
      PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DomTarget) {
        DomElement domElement = ((DomTarget)target).getDomElement();
        if (domElement instanceof ExtensionPoint || domElement instanceof Extension) {
          return createAllPluginDescriptorFilesSearchScope(element);
        }
      }
    }

    if (element instanceof PsiClass &&
        PsiUtil.isIdeaProject(element.getProject()) &&
        ((PsiClass)element).hasModifierProperty(PsiModifier.PUBLIC)) {
      return createAllPluginDescriptorFilesSearchScope(element);
    }
    return null;
  }

  private static SearchScope createAllPluginDescriptorFilesSearchScope(PsiElement element) {
    final Project project = element.getProject();
    final Collection<VirtualFile> pluginXmlFiles =
      DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, project,
                                                    GlobalSearchScope.allScope(project));
    return GlobalSearchScope.filesScope(project, pluginXmlFiles);
  }
}
