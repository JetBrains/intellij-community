package org.jetbrains.plugins.groovy.gradle;

import com.intellij.execution.CantRunException;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;

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
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GroovyScriptRunner() {
      @Override
      public boolean isValidModule(Module module) {
        return GradleLibraryManager.isGradleSdk(ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES));
      }

      @Override
      public boolean ensureRunnerConfigured(Module module, String confName) {
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
                                       Module module,
                                       boolean tests,
                                       VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
        params.setMainClass("org.gradle.BootstrapMain");

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
}
