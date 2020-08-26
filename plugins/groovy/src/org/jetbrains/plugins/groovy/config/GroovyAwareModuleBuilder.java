// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.mvc.GroovySdkForNewModuleWizardStep;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

import javax.swing.*;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * @author peter
 */
public class GroovyAwareModuleBuilder extends JavaModuleBuilder {
  private final @NonNls String myBuilderId;
  private final @Nls(capitalization = Title) String myPresentableName;
  private final @DetailedDescription String myDescription;

  @SuppressWarnings("UnusedDeclaration")
  public GroovyAwareModuleBuilder() {
    this("groovy", GroovyBundle.message("language.groovy"), GroovyBundle.message("module.with.groovy"));
  }

  protected GroovyAwareModuleBuilder(@NonNls String builderId,
                                     @Nls(capitalization = Title) String presentableName,
                                     @DetailedDescription String description) {
    myBuilderId = builderId;
    myPresentableName = presentableName;
    myDescription = description;
  }

  @Nullable
  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    return new GroovySdkForNewModuleWizardStep(this, settingsStep.getContext(), getFramework(), settingsStep);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Override
  public @NonNls String getBuilderId() {
    return myBuilderId;
  }

  @Override
  public Icon getNodeIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public String getPresentableName() {
    return myPresentableName;
  }

  @Override
  public String getGroupName() {
    return "Groovy";
  }

  @Override
  public String getParentGroup() {
    return "Groovy";
  }

  @Override
  public int getWeight() {
    return 60;
  }

  @Nullable
  protected MvcFramework getFramework() {
    return null;
  }
}
