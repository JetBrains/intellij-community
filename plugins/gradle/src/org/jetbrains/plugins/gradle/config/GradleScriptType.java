/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.GradleTaskLocation;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GradleScriptType extends GroovyScriptType {

  private static final Pattern MAIN_CLASS_NAME_PATTERN = Pattern.compile("\nSTARTER_MAIN_CLASS=(.*)\n");

  public static final GroovyScriptType INSTANCE = new GradleScriptType();

  private GradleScriptType() {
    super(GradleConstants.EXTENSION);
  }

  @NotNull
  @Override
  public Icon getScriptIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  public boolean isConfigurationByLocation(@NotNull GroovyScriptRunConfiguration existing, @NotNull Location location) {
    final String params = existing.getScriptParameters();
    if (params == null) {
      return false;
    }

    final List<String> tasks = getTasksTarget(location);
    if (tasks == null) {
      return false;
    }

    String s = StringUtil.join(tasks, " ");
    return params.startsWith(s + " ") || params.equals(s);
  }

  @Override
  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull GroovyScriptRunConfiguration configuration, Location location) {
    List<String> tasks = getTasksTarget(location);
    if (tasks != null) {
      String s = StringUtil.join(tasks, " ");
      configuration.setScriptParameters(s);
      configuration.setName("gradle:" + s);
    }
    RunManagerEx.disableTasks(file.getProject(), configuration, CompileStepBeforeRun.ID, CompileStepBeforeRunNoErrorCheck.ID);
  }

  @Nullable
  private static List<String> getTasksTarget(Location location) {
    if (location instanceof GradleTaskLocation) {
      return ((GradleTaskLocation)location).getTasks();
    }

    PsiElement parent = location.getPsiElement();
    while (parent.getParent() != null && !(parent.getParent() instanceof PsiFile)) {
      parent = parent.getParent();
    }

    if (isCreateTaskMethod(parent)) {
      final GrExpression[] arguments = ((GrMethodCallExpression)parent).getExpressionArguments();
      if (arguments.length > 0 && arguments[0] instanceof GrLiteral && ((GrLiteral)arguments[0]).getValue() instanceof String) {
        return Collections.singletonList((String)((GrLiteral)arguments[0]).getValue());
      }
    }
    else if (parent instanceof GrApplicationStatement) {
      PsiElement shiftExpression = parent.getChildren()[1].getChildren()[0];
      if (GradleResolverUtil.isLShiftElement(shiftExpression)) {
        PsiElement shiftiesChild = shiftExpression.getChildren()[0];
        if (shiftiesChild instanceof GrReferenceExpression) {
          return Collections.singletonList(shiftiesChild.getText());
        }
        else if (shiftiesChild instanceof GrMethodCallExpression) {
          return Collections.singletonList(shiftiesChild.getChildren()[0].getText());
        }
      }
      else if (shiftExpression instanceof GrMethodCallExpression) {
        return Collections.singletonList(shiftExpression.getChildren()[0].getText());
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
      public boolean shouldRefreshAfterFinish() {
        return true;
      }

      @Override
      public boolean isValidModule(@NotNull Module module) {
        GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        return libraryManager.isGradleSdk(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
      }

      @Override
      public boolean ensureRunnerConfigured(@Nullable Module module, RunProfile profile, Executor executor, final Project project) throws ExecutionException {
        if (project != null && profile instanceof GroovyScriptRunConfiguration) {
          GroovyScriptRunConfiguration configuration = (GroovyScriptRunConfiguration)profile;
          String parameters = configuration.getScriptParameters();
          if (parameters != null) {
            // TODO den implement
//            GradleTasksList list = GradleUtil.getToolWindowElement(GradleTasksList.class, project, ExternalSystemDataKeys.RECENT_TASKS_LIST);
//            if (list != null) {
//              ExternalSystemTaskDescriptor descriptor = new ExternalSystemTaskDescriptor(parameters, null);
//              descriptor.setExecutorId(executor.getId());
//              list.setFirst(descriptor);
//              GradleLocalSettings.getInstance(project).setRecentTasks(list.getModel().getTasks());
//            }
          }
        }
        final GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        // TODO den implement
        //if (libraryManager.getGradleHome(module, project) == null) {
        //  int result = 0;
//          int result = Messages.showOkCancelDialog(
//            ExternalSystemBundle.message("gradle.run.no.sdk.text"),
//            ExternalSystemBundle.message("gradle.run.no.sdk.title"),
//            GradleIcons.Gradle
//          );
//          if (result == 0) {
//            ShowSettingsUtil.getInstance().editConfigurable(project, new AbstractExternalProjectConfigurable(project));
//          }
//          if (libraryManager.getGradleHome(module, project) == null) {
//            return false;
//          }
//        }
        return true;
      }

      @Override
      public void configureCommandLine(JavaParameters params,
                                       @Nullable Module module,
                                       boolean tests,
                                       VirtualFile script, GroovyScriptRunConfiguration configuration)
        throws CantRunException
      {
        final Project project = configuration.getProject();
        String scriptParameters = configuration.getScriptParameters();

        final GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
        if (module == null) {
          throw new CantRunException("Target module is undefined");
        }
        String rootProjectPath = module.getOptionValue(ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
        if (StringUtil.isEmpty(rootProjectPath)) {
          throw new CantRunException(String.format("Module '%s' is not backed by gradle", module.getName()));
        }
        final VirtualFile gradleHome = libraryManager.getGradleHome(module, project, rootProjectPath);
        if(gradleHome == null) {
          throw new CantRunException("Gradle home can not be found");
        }

        params.setMainClass(findMainClass(gradleHome, script, project));

        final File[] groovyJars = GroovyConfigUtils.getGroovyAllJars(gradleHome.getPath() + "/lib/");
        if (groovyJars.length > 0) {
          params.getClassPath().add(groovyJars[0].getAbsolutePath());
        }
        else {
          final VirtualFile groovyJar = findGroovyJar(module);
          if (groovyJar != null) {
            params.getClassPath().add(groovyJar);
          }
        }

        final String userDefinedClasspath = System.getProperty("gradle.launcher.classpath");
        if (StringUtil.isNotEmpty(userDefinedClasspath)) {
          params.getClassPath().add(userDefinedClasspath);
        } else {
          final Collection<VirtualFile> roots = libraryManager.getClassRoots(project);
          if (roots != null) {
            params.getClassPath().addVirtualFiles(roots);
          }
        }

        params.getVMParametersList().addParametersString(configuration.getVMParameters());


        params.getVMParametersList().add("-Dgradle.home=" + FileUtil.toSystemDependentName(gradleHome.getPath()));

        setToolsJar(params);

        final String scriptPath = configuration.getScriptPath();
        if (scriptPath == null) {
          throw new CantRunException("Target script is undefined");
        }
        params.getProgramParametersList().add("--build-file");
        params.getProgramParametersList().add(FileUtil.toSystemDependentName(scriptPath));
        params.getProgramParametersList().addParametersString(configuration.getProgramParameters());
        params.getProgramParametersList().addParametersString(scriptParameters);
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
        final String text = StringUtil.convertLineSeparators(VfsUtilCore.loadText(launcher));
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
    if (!FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return baseScope;

    final Collection<VirtualFile> files;
    GlobalSearchScope result = GlobalSearchScope.EMPTY_SCOPE;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      String externalSystemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      if(!GradleConstants.SYSTEM_ID.toString().equals(externalSystemId)) return baseScope;

      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof JdkOrderEntry) {
          GlobalSearchScope scopeForSdk = LibraryScopeCache.getInstance(module.getProject()).getScopeForSdk((JdkOrderEntry)entry);
          result = result.uniteWith(scopeForSdk);
        }
      }

      String modulePath = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
      if(modulePath == null) return result;

      files = GradleBuildClasspathManager.getInstance(file.getProject()).getModuleClasspathEntries(modulePath);

      for (final VirtualFile root : files) {
        result = result.uniteWith(new NonClasspathDirectoryScope(root));
      }
      result = new ExternalModuleBuildGlobalSearchScope(result, modulePath);
    }
    return result;
  }
}
