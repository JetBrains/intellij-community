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
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.GrImportContributorBase;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GriffonDefaultImportContributor extends GrImportContributorBase {

  private static Couple<List<String>> getDefaultImports(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, new CachedValueProvider<Couple<List<String>>>() {
      @Override
      public Result<Couple<List<String>>> compute() {
        PsiPackage aPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage("META-INF");
        if (aPackage != null) {
          for (PsiDirectory directory : aPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module))) {
            PsiFile file = directory.findFile("griffon-default-imports.properties");
            if (file instanceof PropertiesFile) {
              List<String> modelImports = tokenize(((PropertiesFile)file).findPropertyByKey("models"));
              List<String> viewImports = tokenize(((PropertiesFile)file).findPropertyByKey("views"));
              return Result.create(Couple.of(modelImports, viewImports), PsiModificationTracker.MODIFICATION_COUNT);
            }
          }
        }

        return Result.create(Couple.<List<String>>of(new ArrayList<>(), new ArrayList<>()),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }

      private List<String> tokenize(IProperty models) {
        List<String> modelImports = new ArrayList<>();
        if (models != null) {
          String value = models.getValue();
          if (value != null) {
            String[] split = value.split(", ");
            for (String s : split) {
              modelImports.add(StringUtil.trimEnd(s, "."));
            }
          }
        }
        return modelImports;
      }
    });
  }

  @NotNull
  @Override
  public List<String> appendImplicitlyImportedPackages(@NotNull GroovyFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    MvcFramework framework = MvcFramework.getInstance(module);
    if (framework instanceof GriffonFramework) {
      ArrayList<String> result = new ArrayList<>();
      result.add("griffon.core");
      result.add("griffon.util");

      VirtualFile griffonApp = framework.findAppDirectory(file);
      if (griffonApp != null) {
        VirtualFile models = griffonApp.findChild("models");
        VirtualFile views = griffonApp.findChild("views");
        VirtualFile vFile = file.getOriginalFile().getVirtualFile();

        assert vFile != null;
        assert module != null;
        if (models != null && VfsUtilCore.isAncestor(models, vFile, true)) {
          result.addAll(getDefaultImports(module).first);
        }
        else if (views != null && VfsUtilCore.isAncestor(views, vFile, true)) {
          result.addAll(getDefaultImports(module).second);
        }
      }

      return result;
    }

    return Collections.emptyList();
  }
}
