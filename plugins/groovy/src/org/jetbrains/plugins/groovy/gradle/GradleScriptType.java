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
package org.jetbrains.plugins.groovy.gradle;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GradleScriptType extends GroovyScriptType {
  @NonNls private static final String GRADLE_EXTENSION = "gradle";
  private static final Pattern MAIN_CLASS_NAME_PATTERN = Pattern.compile("\nSTARTER_MAIN_CLASS=(.*)\n");

  @Override
  public boolean isSpecificScriptFile(GroovyFile file) {
    return GRADLE_EXTENSION.equals(file.getViewProvider().getVirtualFile().getExtension());
  }

  @NotNull
  @Override
  public Icon getScriptIcon() {
    return GradleLibraryManager.GRADLE_ICON;
  }

  @Override
   public boolean isConfigurationByLocation(@NotNull GroovyScriptRunConfiguration existing, @NotNull Location location) {
    final String params = existing.scriptParams;
    final String s = getTaskTarget(location);
    return s != null && params != null && (params.startsWith(s + " ") || params.equals(s));
  }

  @Override
  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull GroovyScriptRunConfiguration configuration, Location location) {
    String target = getTaskTarget(location);
    if (target != null) {
      configuration.scriptParams = target;
      configuration.setName(configuration.getName() + "." + target);
    }

    
    final CompileStepBeforeRun.MakeBeforeRunTask runTask =
      RunManagerEx.getInstanceEx(file.getProject()).getBeforeRunTask(configuration, CompileStepBeforeRun.ID);
    if (runTask != null) {
      runTask.setEnabled(false);
    }
  }

  @Nullable
  private static String getTaskTarget(Location location) {
    PsiElement parent = location.getPsiElement();
    while (parent.getParent() != null && !(parent.getParent() instanceof PsiFile)) {
      parent = parent.getParent();
    }

    if (isCreateTaskMethod(parent)) {
      final GrExpression[] arguments = ((GrMethodCallExpression)parent).getExpressionArguments();
      if (arguments.length > 0 && arguments[0] instanceof GrLiteral && ((GrLiteral)arguments[0]).getValue() instanceof String) {
        return (String)((GrLiteral)arguments[0]).getValue();
      }
    }
    else if (parent instanceof GrApplicationStatement) {
      PsiElement shiftExpression = parent.getChildren()[1].getChildren()[0];
      if (shiftExpression instanceof GrShiftExpressionImpl) {
        PsiElement shiftiesChild = shiftExpression.getChildren()[0];
        if (shiftiesChild instanceof GrReferenceExpression) {
          return shiftiesChild.getText();
        }
        else if (shiftiesChild instanceof GrMethodCallExpression) {
          return shiftiesChild.getChildren()[0].getText();
        }
      }
      else if (shiftExpression instanceof GrMethodCallExpression) {
        return shiftExpression.getChildren()[0].getText();
      }
    }
    
    return null;
  }

  private static boolean isCreateTaskMethod(PsiElement parent) {
    return parent instanceof GrMethodCallExpression && PsiUtil.isMethodCall((GrMethodCallExpression)parent, "createTask");
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GroovyScriptRunner() {
      @Override
      public boolean isValidModule(@NotNull Module module) {
        return GradleLibraryManager.isGradleSdk(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
      }

      @Override
      public boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) throws ExecutionException {
        if (GradleLibraryManager.getSdkHome(module, project) == null) {
          int result = Messages
            .showOkCancelDialog("Gradle is not configured. Do you want to configure it?", "Configure Gradle SDK",
                                GradleLibraryManager.GRADLE_ICON);
          if (result == 0) {
            final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
            util.editConfigurable(project, util.createProjectConfigurable(project, GradleConfigurable.class));
          }
          if (GradleLibraryManager.getSdkHome(module, project) == null) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void configureCommandLine(JavaParameters params,
                                       @Nullable Module module,
                                       boolean tests,
                                       VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
        final Project project = configuration.getProject();
        final VirtualFile gradleHome = GradleLibraryManager.getSdkHome(module, project);
        assert gradleHome != null;

        params.setMainClass(findMainClass(gradleHome, script, project));

        final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(gradleHome.getPath() + "/lib/", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
        if (groovyJars.length > 0) {
          params.getClassPath().add(groovyJars[0].getAbsolutePath());
        } else if (module != null) {
          final VirtualFile groovyJar = findGroovyJar(module);
          if (groovyJar != null) {
            params.getClassPath().add(groovyJar);
          }
        }

        final String userDefinedClasspath = System.getProperty("gradle.launcher.classpath");
        if (StringUtil.isNotEmpty(userDefinedClasspath)) {
          params.getClassPath().add(userDefinedClasspath);
        } else {
          params.getClassPath().addAllFiles(
            GroovyUtils.getFilesInDirectoryByPattern(gradleHome.getPath() + "/lib/", GradleLibraryManager.ANY_GRADLE_JAR_FILE_PATTERN));
        }

        params.getVMParametersList().addParametersString(configuration.vmParams);


        params.getVMParametersList().add("-Dgradle.home=" + FileUtil.toSystemDependentName(gradleHome.getPath()) + "");

        setToolsJar(params);

        params.getProgramParametersList().add("--build-file");
        params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));
        params.getProgramParametersList().addParametersString(configuration.scriptParams);
      }
    };
  }

  @NotNull
  private static String findMainClass(VirtualFile gradleHome, VirtualFile script, Project project) {
    final String userDefined = System.getProperty("gradle.launcher.class");
    if (StringUtil.isNotEmpty(userDefined)) {
      return userDefined;
    }

    VirtualFile launcher = gradleHome.findFileByRelativePath("bin/gradle");
    if (launcher == null) {
      launcher = gradleHome.findFileByRelativePath("bin/gradle.bat");
    }
    if (launcher != null) {
      try {
        final String text = StringUtil.convertLineSeparators(VfsUtil.loadText(launcher));
        final Matcher matcher = MAIN_CLASS_NAME_PATTERN.matcher(text);
        if (matcher.find()) {
          String candidate = matcher.group(1);
          if (StringUtil.isNotEmpty(candidate)) {
            return candidate;
          }
        }
      }
      catch (IOException ignored) {
      }
    }

    final PsiFile grFile = PsiManager.getInstance(project).findFile(script);
    if (grFile != null && JavaPsiFacade.getInstance(project).findClass("org.gradle.BootstrapMain", grFile.getResolveScope()) != null) {
      return "org.gradle.BootstrapMain";
    }

    return "org.gradle.launcher.GradleMain";
  }

  @Override
  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      if (GradleLibraryManager.getSdkHomeFromClasspath(module) != null) {
        return baseScope;
      }
    }

    final GradleSettings gradleSettings = GradleSettings.getInstance(file.getProject());
    final VirtualFile home = gradleSettings.getSdkHome();
    if (home == null) {
      return baseScope;
    }

    final List<VirtualFile> files = gradleSettings.getClassRoots();
    if (files.isEmpty()) {
      return baseScope;
    }

    GlobalSearchScope result = baseScope;
    for (final VirtualFile root : files) {
      result = result.uniteWith(new NonClasspathDirectoryScope(root));
    }
    return result;
  }


  @Override
  public List<String> appendImplicitImports(@NotNull GroovyFile file) {
    return Arrays.asList(
      "org.gradle",
      "org.gradle.util",
      "org.gradle.api",
      "org.gradle.api.artifacts",
      "org.gradle.api.artifacts.dsl",
      "org.gradle.api.artifacts.specs",
      "org.gradle.api.dependencies",
      "org.gradle.api.execution",
      "org.gradle.api.file",
      "org.gradle.api.logging",
      "org.gradle.api.initialization",
      "org.gradle.api.invocation",
      "org.gradle.api.plugins",
      "org.gradle.api.plugins.quality",
      "org.gradle.api.specs",
      "org.gradle.api.tasks",
      "org.gradle.api.tasks.bundling",
      "org.gradle.api.tasks.compile",
      "org.gradle.api.tasks.javadoc",
      "org.gradle.api.tasks.testing",
      "org.gradle.api.tasks.util",
      "org.gradle.api.tasks.wrapper"

    );
  }
}
