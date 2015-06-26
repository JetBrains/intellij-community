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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class RuntimeModuleReference extends PsiReferenceBase<PsiElement> {
  public RuntimeModuleReference(@NotNull PsiElement element) {
    super(element);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    Module module = ModuleManager.getInstance(myElement.getProject()).findModuleByName(getValue());
    if (module == null) return null;
    return PomService.convertToPsi(myElement.getProject(), new IdeaModulePomTarget(module));
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Module[] modules = ModuleManager.getInstance(myElement.getProject()).getModules();
    List<String> result = new ArrayList<String>();
    for (Module module : modules) {
      result.add(module.getName());
    }
    return ArrayUtil.toStringArray(result);
  }
}
