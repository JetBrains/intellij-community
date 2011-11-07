package org.jetbrains.android.compiler;

import com.intellij.compiler.impl.AdditionalCompileScopeProvider;
import com.intellij.compiler.impl.CompositeScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAdditionalCompileScopeProvider extends AdditionalCompileScopeProvider {
  @Override
  public CompileScope getAdditionalScope(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull Project project) {
    if (ProjectFacetManager.getInstance(project)
          .getFacets(AndroidFacet.ID, baseScope.getAffectedModules()).size() == 0) {
      return null;
    }

    final List<Module> genModules = new ArrayList<Module>();

    for (Module module : baseScope.getAffectedModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        continue;
      }

      AndroidCompileUtil.createGenModulesAndSourceRoots(facet);

      final Module genModule = AndroidCompileUtil.getGenModule(module);
      if (genModule != null) {
        genModules.add(genModule);
      }
    }

    if (genModules.size() == 0) {
      return null;
    }

    final Module[] genModulesArray = genModules.toArray(new Module[genModules.size()]);
    return new CompositeScope(baseScope, new ModuleCompileScope(project, genModulesArray, false));
  }
}
