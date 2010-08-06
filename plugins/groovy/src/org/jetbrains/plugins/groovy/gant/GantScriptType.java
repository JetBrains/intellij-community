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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import com.intellij.compiler.options.CompileStepBeforeRun;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public class GantScriptType extends GroovyScriptType {
  @NonNls public static final String DEFAULT_EXTENSION = "gant";

  public boolean isSpecificScriptFile(final GroovyFile file) {
    return GantUtils.isGantScriptFile(file);
  }

  @NotNull
  public Icon getScriptIcon() {
    return GantIcons.GANT_ICON_16x16;
  }

  @Override
  public GroovyScriptRunner getRunner() {
    return new GantRunner();
  }

  @Override
  public boolean isConfigurationByLocation(@NotNull GroovyScriptRunConfiguration existing, @NotNull Location place) {
    final String params = existing.scriptParams;
    final String s = getTargetName(place);
    return s != null && params != null && (params.startsWith(s + " ") || params.equals(s));
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
        if (label != null && GantUtils.isPlainIdentifier(label)) {
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
      configuration.scriptParams = target;
      configuration.setName(configuration.getName() + "." + target);
    }
    final CompileStepBeforeRun.MakeBeforeRunTask runTask =
      RunManagerEx.getInstanceEx(file.getProject()).getBeforeRunTask(configuration, CompileStepBeforeRun.ID);
    if (runTask != null) {
      runTask.setEnabled(false);
    }
  }

  public static List<VirtualFile> additionalScopeFiles(@NotNull GroovyFile file) {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
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
    GlobalSearchScope result = baseScope;
    for (final VirtualFile root : additionalScopeFiles(file)) {
      result = result.uniteWith(new NonClasspathDirectoryScope(root));
    }
    return result;
  }
}
