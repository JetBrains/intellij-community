/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public class GroovyScriptRunConfigurationType implements LocatableConfigurationType {
  private final GroovyFactory myConfigurationFactory;

  public GroovyScriptRunConfigurationType() {
    myConfigurationFactory = new GroovyFactory(this);
  }

  public String getDisplayName() {
    return "Groovy Script";
  }

  public String getConfigurationTypeDescription() {
    return "Groovy Script";
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NonNls
  @NotNull
  public String getId() {
    return "GroovyScriptRunConfiguration";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    final PsiElement element = location.getPsiElement();
    final PsiClass clazz = getScriptClass(element);
    if (clazz == null) return null;
    return createConfiguration(clazz);
  }

  public boolean isConfigurationByLocation(RunConfiguration configuration, Location location) {
    final String path = ((GroovyScriptRunConfiguration)configuration).scriptPath;
    if (path == null) {
      return false;
    }

    final PsiFile file = location.getPsiElement().getContainingFile();
    if (file == null) {
      return false;
    }

    final VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) {
      return false;
    }
    
    return FileUtil.toSystemIndependentName(path).equals(vfile.getPath());
  }

  private RunnerAndConfigurationSettings createConfiguration(final PsiClass aClass) {
    final Project project = aClass.getProject();
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).createConfiguration("", myConfigurationFactory);
    final GroovyScriptRunConfiguration configuration = (GroovyScriptRunConfiguration) settings.getConfiguration();
    final PsiFile file = aClass.getContainingFile();
    final PsiDirectory dir = file.getContainingDirectory();
    assert dir != null;
    configuration.setWorkDir(dir.getVirtualFile().getPath());
    final VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;
    configuration.scriptPath = vFile.getPath();
    RunConfigurationModule module = configuration.getConfigurationModule();


    String name = getConfigurationName(aClass, module);
    configuration.setName(name);
    configuration.setModule(JavaExecutionUtil.findModule(aClass));
    return settings;
  }

  private static String getConfigurationName(PsiClass aClass, RunConfigurationModule module) {
    String qualifiedName = aClass.getQualifiedName();
    Project project = module.getProject();
    if (qualifiedName != null) {
      PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName.replace('$', '.'), GlobalSearchScope.projectScope(project));
      if (psiClass != null) {
        return psiClass.getName();
      } else {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == qualifiedName.length() - 1) {
          return qualifiedName;
        }
        return qualifiedName.substring(lastDot + 1, qualifiedName.length());
      }
    }
    return module.getModuleName();
  }

  @Nullable
  private static PsiClass getScriptClass(PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;
    return ((GroovyFile) file).getScriptClass();
  }

  public static GroovyScriptRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GroovyScriptRunConfigurationType.class);
  }

  public static class GroovyFactory extends ConfigurationFactory {
    public GroovyFactory(LocatableConfigurationType type) {
      super(type);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new GroovyScriptRunConfiguration("Groovy Script", project, this);
    }

  }
}
