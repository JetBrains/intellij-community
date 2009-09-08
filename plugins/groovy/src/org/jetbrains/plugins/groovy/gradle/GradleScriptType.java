package org.jetbrains.plugins.groovy.gradle;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class GradleScriptType extends GroovyScriptType {
  @NonNls private static final String GRADLE_EXTENSION = "gradle";

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
  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull GroovyScriptRunConfiguration configuration, Location location) {
    final PsiElement element = location.getPsiElement();
    PsiElement pp = element.getParent();
    PsiElement parent = element;
    while (!(pp instanceof PsiFile) && pp != null) {
      pp = pp.getParent();
      parent = parent.getParent();
    }
    if (pp != null && parent instanceof GrMethodCallExpression && PsiUtil.isMethodCall((GrMethodCallExpression)parent, "createTask")) {
      final GrExpression[] arguments = ((GrMethodCallExpression)parent).getArgumentList().getExpressionArguments();
      if (arguments.length > 0 && arguments[0] instanceof GrLiteral && ((GrLiteral)arguments[0]).getValue() instanceof String) {
        String target = (String)((GrLiteral)arguments[0]).getValue();
        configuration.scriptParams = target;
        configuration.setName(configuration.getName() + "." + target);
      }
    }
    final CompileStepBeforeRun.MakeBeforeRunTask runTask =
      RunManagerEx.getInstanceEx(element.getProject()).getBeforeRunTask(configuration, CompileStepBeforeRun.ID);
    if (runTask != null) {
      runTask.setEnabled(false);
    }
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GroovyScriptRunner() {
      @Override
      public boolean isValidModule(@NotNull Module module) {
        return GradleLibraryManager.isGradleSdk(ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES));
      }

      @Override
      public boolean ensureRunnerConfigured(@Nullable Module module, String confName, final Project project) throws ExecutionException {
        if (GradleLibraryManager.getSdkHome(module, project) == null) {
          int result = Messages
            .showOkCancelDialog("Gradle is not configured. Do you want to configure it?", "Configure Gradle SDK",
                                GradleLibraryManager.GRADLE_ICON);
          if (result == 0) {
            final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
            util.editConfigurable(project, util.findProjectConfigurable(project, GradleConfigurable.class));
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
        params.setMainClass("org.gradle.BootstrapMain");

        final VirtualFile gradleHome = GradleLibraryManager.getSdkHome(module, configuration.getProject());
        assert gradleHome != null;
        final File[] groovyJars = GroovyUtils.getFilesInDirectoryByPattern(gradleHome.getPath() + "/lib/", GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN);
        if (groovyJars.length > 0) {
          params.getClassPath().add(groovyJars[0].getAbsolutePath());
        } else if (module != null) {
          final VirtualFile groovyJar = findGroovyJar(module);
          if (groovyJar != null) {
            params.getClassPath().add(groovyJar);
          }
        }

        final File[] gradleJars = GroovyUtils.getFilesInDirectoryByPattern(gradleHome.getPath() + "/lib/", GradleLibraryManager.GRADLE_JAR_FILE_PATTERN);
        if (gradleJars.length > 0) {
          params.getClassPath().add(gradleJars[0].getAbsolutePath());
        }

        params.getVMParametersList().addParametersString(configuration.vmParams);


        params.getVMParametersList().add("-Dgradle.home=\"" + FileUtil.toSystemDependentName(gradleHome.getPath()) + "\"");

        setToolsJar(params);

        params.getProgramParametersList().add("--build-file");
        params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));
        params.getProgramParametersList().addParametersString(configuration.scriptParams);
      }
    };
  }

  @Override
  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      final String sdkHome = GantUtils.getSdkHomeFromClasspath(module);
      if (sdkHome != null) {
        return baseScope;
      }
    }

    final GradleSettings gantSettings = GradleSettings.getInstance(file.getProject());
    final VirtualFile home = gantSettings.getSdkHome();
    if (home == null) {
      return baseScope;
    }

    final List<VirtualFile> files = gantSettings.getClassRoots();
    if (files.isEmpty()) {
      return baseScope;
    }

    GlobalSearchScope result = baseScope;
    for (final VirtualFile root : files) {
      result = result.uniteWith(new NonClasspathDirectoryScope(root));
    }
    return result;
  }


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
