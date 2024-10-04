// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.application.options.colors.fileStatus.FileStatusColorsConfigurable;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Weighted;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfigurableProvider;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictConfigurable;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel;
import com.intellij.openapi.vcs.impl.VcsEP;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.options.ex.ConfigurableWrapper.wrapConfigurable;
import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

@ApiStatus.Internal
public final class VcsManagerConfigurable extends SearchableConfigurable.Parent.Abstract
  implements Weighted, ConfigurableGroup, Configurable.NoScroll, Configurable.WithEpDependencies {

  private static final String ID = "project.propVCSSupport.Mappings";
  private static final int GROUP_WEIGHT = 45;

  @NotNull private final Project myProject;

  public VcsManagerConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NonNls @NotNull String getId() {
    return ID;
  }

  @Override
  public @NonNls String getHelpTopic() {
    return VcsMappingConfigurable.HELP_ID;
  }

  @Override
  public int getWeight() {
    return GROUP_WEIGHT;
  }

  @Override
  public @NlsContexts.ConfigurableName String getDisplayName() {
    return VcsBundle.message("version.control.main.configurable.name");
  }

  @Override
  public @NlsContexts.DetailedDescription String getDescription() {
    return VcsBundle.message("version.control.main.configurable.description");
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Arrays.asList(
      VcsEP.EP_NAME, VcsConfigurableProvider.EP_NAME
    );
  }

  @Override
  protected Configurable[] buildConfigurables() {
    List<Configurable> result = new ArrayList<>();

    result.add(new VcsGeneralSettingsConfigurable(myProject));
    result.add(new VcsMappingConfigurable(myProject));
    if (Registry.is("vcs.ignorefile.generation", true)) {
      result.add(new IgnoredSettingsPanel(myProject));
    }
    result.add(new IssueNavigationConfigurable(myProject));
    result.add(new ChangelistConflictConfigurable(myProject));
    result.add(new CommitDialogConfigurable(myProject));
    result.add(new ShelfProjectConfigurable(myProject));
    for (VcsConfigurableProvider provider : VcsConfigurableProvider.EP_NAME.getExtensionList()) {
      addIfNotNull(result, provider.getConfigurable(myProject));
    }

    result.add(new FileStatusColorsConfigurable());

    for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllSupportedVcss()) {
      Configurable configurable = vcs.getConfigurable();
      if (configurable != null) {
        result.add(wrapConfigurable(new VcsConfigurableEP(myProject, vcs, configurable)));
      }
    }

    return result.toArray(new Configurable[0]);
  }

  @NotNull
  @NonNls
  private static String getVcsConfigurableId(@NotNull String vcsName) {
    return "vcs." + vcsName;
  }

  private static class VcsConfigurableEP extends ConfigurableEP<Configurable> {
    private static final int WEIGHT = -500;

    private final Configurable myConfigurable;

    VcsConfigurableEP(@NotNull Project project, @NotNull AbstractVcs vcs, @NonNls Configurable configurable) {
      super(project);

      myConfigurable = configurable;
      displayName = vcs.getDisplayName();
      id = getVcsConfigurableId(vcs.getName());
      groupWeight = WEIGHT;
    }

    @NotNull
    @Override
    protected ConfigurableEP.ObjectProducer createProducer() {
      return new ObjectProducer() {
        @Override
        protected Object createElement() {
          return myConfigurable;
        }

        @Override
        protected boolean canCreateElement() {
          return true;
        }

        @Override
        protected Class<?> getType() {
          return SearchableConfigurable.class;
        }
      };
    }
  }
}
