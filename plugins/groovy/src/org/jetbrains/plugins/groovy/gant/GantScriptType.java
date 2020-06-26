// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public final class GantScriptType extends GroovyRunnableScriptType {
  @NonNls public static final String DEFAULT_EXTENSION = "gant";

  public static final GroovyScriptType INSTANCE = new GantScriptType();

  private GantScriptType() {
    super("gant");
  }

  @Override
  @NotNull
  public Icon getScriptIcon() {
    return JetgroovyIcons.Groovy.Gant_16x16;
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GantRunner();
  }

  @Override
  public boolean isConfigurationByLocation(@NotNull GroovyScriptRunConfiguration existing, @NotNull Location place) {
    final String params = existing.getProgramParameters();
    final String targetName = getTargetName(place);
    if (targetName == null) {
      return StringUtil.isEmpty(params);
    }
    return params != null && (params.startsWith(targetName + " ") || params.equals(targetName));
  }

  @Nullable
  private static String getTargetName(Location location) {
    PsiElement parent = location.getPsiElement();
    while (!(parent.getParent() instanceof PsiFile) && parent.getParent() != null) {
      parent = parent.getParent();
    }
    if (parent instanceof GrMethodCallExpression && PsiUtil.isMethodCall((GrMethodCallExpression)parent, "target")) {
      final GrNamedArgument[] args = ((GrMethodCallExpression)parent).getNamedArguments();
      if (args.length == 1) {
        final GrArgumentLabel label = args[0].getLabel();
        if (label != null) {
          return label.getName();
        }
      }
      return null;
    }
    return null;
  }

  @Override
  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull GroovyScriptRunConfiguration configuration, Location location) {
    String target = getTargetName(location);
    if (target != null) {
      configuration.setProgramParameters(target);
      configuration.setName(configuration.getName() + "." + target);
    }
    RunManagerEx.disableTasks(file.getProject(), configuration, CompileStepBeforeRun.ID, CompileStepBeforeRunNoErrorCheck.ID);
  }

  public static List<VirtualFile> additionalScopeFiles(@NotNull GroovyFile file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      final String sdkHome = GantUtils.getSdkHomeFromClasspath(module);
      if (sdkHome != null) {
        return Collections.emptyList();
      }
    }

    final GantSettings gantSettings = GantSettings.getInstance(file.getProject());
    final VirtualFile home = gantSettings.getSdkHome();
    if (home == null) {
      return Collections.emptyList();
    }

    return gantSettings.getClassRoots();
  }

  @Override
  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return baseScope.uniteWith(new NonClasspathDirectoriesScope(additionalScopeFiles(file)));
  }
}
