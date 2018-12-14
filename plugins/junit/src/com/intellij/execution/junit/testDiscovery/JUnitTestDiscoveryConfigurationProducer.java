// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestsPattern;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoveryConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import com.intellij.rt.execution.junit.JUnitStarter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class JUnitTestDiscoveryConfigurationProducer extends TestDiscoveryConfigurationProducer {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  @Override
  protected void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position) {
    ((JUnitConfiguration)configuration).beFromSourcePosition(position);
  }

  @Override
  protected Pair<String, String> getPosition(JavaTestConfigurationBase configuration) {
    final JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    if (data.TEST_OBJECT.equals(JUnitConfiguration.BY_SOURCE_POSITION)) {
      return Pair.create(data.getMainClassName(), data.getMethodName());
    }
    return null;
  }

  @Override
  public boolean isApplicable(@NotNull Location<PsiMethod> testMethod) {
    return JUnitUtil.isTestMethod(testMethod);
  }

  @NotNull
  @Override
  public RunProfileState createProfile(@NotNull Location<PsiMethod>[] testMethods,
                                       Module module,
                                       RunConfiguration configuration,
                                       ExecutionEnvironment environment) {
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    data.setPatterns(collectMethodPatterns(testMethods));
    data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
    Map<Module, Module> toRoot = splitModulesIntoChunks(testMethods, module);
    return new TestsPattern((JUnitConfiguration)configuration, environment) {
      @Override
      protected boolean forkPerModule() {
        return module == null;
      }

      @Override
      protected void fillForkModule(Map<Module, List<String>> perModule, Module module, String name) {
        super.fillForkModule(perModule, toRoot.get(module), name);
      }

      @Override
      protected String getRunner() {
        return JUnitStarter.JUNIT4_PARAMETER;
      }
    };
  }

  private static Map<Module, Module> splitModulesIntoChunks(@NotNull Location<PsiMethod>[] testMethods, Module module) {
    Map<Module, Module> toRoot = new HashMap<>();
    if (module == null) {
      Set<Module> usedModules = Arrays.stream(testMethods).map(Location::getModule).collect(Collectors.toSet());
      while (!usedModules.isEmpty()) {
        Map<Module, Set<Module>> allDeps = new HashMap<>();
        for (Module usedModule : usedModules) {
          List<Module> rootModules = ModuleUtilCore.getAllDependentModules(usedModule);
          for (Module rootModule : rootModules) {
            allDeps.computeIfAbsent(rootModule, __ -> new LinkedHashSet<>()).add(usedModule);
          }
          allDeps.computeIfAbsent(usedModule, __ -> new LinkedHashSet<>()).add(usedModule);
        }


        Optional<Map.Entry<Module, Set<Module>>> maxDependency =
          allDeps.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size()));

        if (maxDependency.isPresent()) {
          Map.Entry<Module, Set<Module>> entry = maxDependency.get();
          Module rootModule = entry.getKey();
          Set<Module> srcModules = entry.getValue();
          for (Module srcModule : srcModules) {
            toRoot.put(srcModule, rootModule);
          }
          usedModules.removeAll(srcModules);
        }
        else {
          break;
        }
      }
    }
    return toRoot;
  }
}
