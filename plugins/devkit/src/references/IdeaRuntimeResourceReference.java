/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.module.RuntimeResourceRoot;
import org.jetbrains.idea.devkit.module.RuntimeResourcesConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class IdeaRuntimeResourceReference extends ModuleResourceReferenceBase {
  public IdeaRuntimeResourceReference(@NotNull PsiElement element, @NotNull PsiLiteral moduleNameElement) {
    super(element, moduleNameElement);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    Module module = resolveModule();
    if (module == null) return null;
    RuntimeResourceRoot root = RuntimeResourcesConfiguration.getInstance(module).getRoot(getValue());
    return root != null ? PomService.convertToPsi(module.getProject(), new IdeaRuntimeResourcePomTarget(root, module)) : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Module module = resolveModule();
    if (module == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    List<String> result = new ArrayList<String>();
    for (RuntimeResourceRoot root : RuntimeResourcesConfiguration.getInstance(module).getRoots()) {
      result.add(root.getName());
    }
    return ArrayUtil.toStringArray(result);
  }

  private static class IdeaRuntimeResourcePomTarget implements PomTarget {
    private final RuntimeResourceRoot myRoot;
    private final Module myModule;

    public IdeaRuntimeResourcePomTarget(RuntimeResourceRoot root, Module module) {
      myRoot = root;
      myModule = module;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
      ProjectSettingsService.getInstance(myModule.getProject()).openModuleSettings(myModule);
    }

    @Override
    public boolean canNavigate() {
      return ProjectSettingsService.getInstance(myModule.getProject()).canOpenModuleSettings();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }
}
