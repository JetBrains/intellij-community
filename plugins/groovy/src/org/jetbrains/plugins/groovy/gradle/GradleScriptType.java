package org.jetbrains.plugins.groovy.gradle;

import com.intellij.execution.CantRunException;
import com.intellij.execution.Location;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.compiler.options.CompileStepBeforeRun;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;
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
        if (module == null) {
          throw new ExecutionException("Module is not specified");
        }

        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final Sdk sdk = rootManager.getSdk();
        if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
          throw CantRunException.noJdkForModule(module);
        }

        if (!isValidModule(module)) {
          int result = Messages
            .showOkCancelDialog("Gradle is not configured. Do you want to configure it?", "Configure Gradle SDK",
                                GradleLibraryManager.GRADLE_ICON);
          if (result == 0) {
            ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
          }
          if (!isValidModule(module)) {
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

        assert module != null;
        final VirtualFile groovyJar = findGroovyJar(module);
        if (groovyJar != null) {
          params.getClassPath().add(groovyJar);
        }
        params.getClassPath().add(GradleLibraryManager.findGradleJar(module));

        params.getVMParametersList().addParametersString(configuration.vmParams);

        final VirtualFile gradleHome = GradleLibraryManager.getSdkHome(module);
        assert gradleHome != null;
        params.getVMParametersList().add("-Dgradle.home=\"" + FileUtil.toSystemDependentName(gradleHome.getPath()) + "\"");

        setToolsJar(params);

        params.getProgramParametersList().add("--build-file");
        params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));
        params.getProgramParametersList().addParametersString(configuration.scriptParams);
      }
    };
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
