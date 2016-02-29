/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit;

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
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;

/**
 * @author peter
 */
public class DevKitUseScopeEnlarger extends UseScopeEnlarger {

  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    if (element instanceof PomTargetPsiElement) {
      PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DomTarget) {
        DomElement domElement = ((DomTarget)target).getDomElement();
        if (domElement instanceof ExtensionPoint) {
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
