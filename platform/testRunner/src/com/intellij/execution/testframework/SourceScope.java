// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public abstract class SourceScope {
  public abstract GlobalSearchScope getGlobalSearchScope();
  public abstract Project getProject();
  public abstract GlobalSearchScope getLibrariesScope();

  private static @NotNull Map<Module, Collection<Module>> buildAllDependencies(@NotNull Project project) {
    Graph<Module> graph = ModuleManager.getInstance(project).moduleGraph();
    Map<Module, Collection<Module>> result = new HashMap<>();
    for (final Module module : graph.getNodes()) {
      buildDependenciesForModule(module, graph, result);
    }
    return result;
  }

  private static void buildDependenciesForModule(@NotNull Module module, final Graph<Module> graph, Map<Module, Collection<Module>> map) {
    final Set<Module> deps = new HashSet<>();
    map.put(module, deps);

    new Object() {
      void traverse(Module m) {
        for (Iterator<Module> iterator = graph.getIn(m); iterator.hasNext();) {
          final Module dep = iterator.next();
          if (!deps.contains(dep)) {
            deps.add(dep);
            traverse(dep);
          }
        }
      }
    }.traverse(module);
  }

  private abstract static class ModuleSourceScope extends SourceScope {
    private final Project myProject;

    ModuleSourceScope(final Project project) {
      myProject = project;
    }

    @Override
    public Project getProject() {
      return myProject;
    }

  }

  public static SourceScope wholeProject(final Project project) {
    return new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return GlobalSearchScope.allScope(project);
      }

      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public Module[] getModulesToCompile() {
        return ModuleManager.getInstance(project).getModules();
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        return getGlobalSearchScope();
      }
    };
  }

  public static SourceScope modulesWithDependencies(final Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    return new ModuleSourceScope(modules[0].getProject()) {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return evaluateScopesAndUnite(modules, module -> module.getModuleRuntimeScope(true));
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        return evaluateScopesAndUnite(modules, module -> new ModuleWithDependenciesAndLibsDependencies(module));
      }

      @Override
      public Module[] getModulesToCompile() {
        return modules;
      }
    };
  }

  private interface ScopeForModuleEvaluator {
    GlobalSearchScope evaluate(Module module);
  }
  private static GlobalSearchScope evaluateScopesAndUnite(final Module[] modules, final ScopeForModuleEvaluator evaluator) {
    GlobalSearchScope[] scopes =
      ContainerUtil.map2Array(modules, GlobalSearchScope.class, module -> evaluator.evaluate(module));
    return GlobalSearchScope.union(scopes);
  }

  public static SourceScope modules(final Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    return new ModuleSourceScope(modules[0].getProject()) {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return evaluateScopesAndUnite(modules, module -> GlobalSearchScope.moduleScope(module));
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        return evaluateScopesAndUnite(modules, module -> GlobalSearchScope.moduleWithLibrariesScope(module));
      }

      @Override
      public Module[] getModulesToCompile() {
        return modules;
      }
    };
  }

  public abstract Module[] getModulesToCompile();

  private static class ModuleWithDependenciesAndLibsDependencies extends GlobalSearchScope {
    private final GlobalSearchScope myMainScope;
    private final List<GlobalSearchScope> myScopes = new ArrayList<>();

    ModuleWithDependenciesAndLibsDependencies(final Module module) {
      super(module.getProject());
      myMainScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      final Map<Module, Collection<Module>> map = buildAllDependencies(module.getProject());
      final Collection<Module> modules = map.get(module);
      for (final Module dependency : modules) {
        myScopes.add(GlobalSearchScope.moduleWithLibrariesScope(dependency));
      }
    }

    @Override
    public boolean contains(final @NotNull VirtualFile file) {
      return findScopeFor(file) != null;
    }

    @Override
    public int compare(final @NotNull VirtualFile file1, final @NotNull VirtualFile file2) {
      final GlobalSearchScope scope = findScopeFor(file1);
      assert scope != null;
      if (scope.contains(file2)) return scope.compare(file1, file2);
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(final @NotNull Module aModule) {
      return myMainScope.isSearchInModuleContent(aModule);
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public @NotNull @Unmodifiable Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return myMainScope.getUnloadedModulesBelongingToScope();
    }

    private @Nullable GlobalSearchScope findScopeFor(final VirtualFile file) {
      if (myMainScope.contains(file)) return myMainScope;
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, size = myScopes.size(); i < size; i++) {
        GlobalSearchScope scope = myScopes.get(i);
        if (scope.contains(file)) return scope;
      }
      return null;
    }
  }
}
