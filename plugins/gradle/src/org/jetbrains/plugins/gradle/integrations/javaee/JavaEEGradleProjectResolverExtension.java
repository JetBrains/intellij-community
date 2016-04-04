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
package org.jetbrains.plugins.gradle.integrations.javaee;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.DependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.data.*;
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * {@link JavaEEGradleProjectResolverExtension} provides JavaEE project info based on gradle tooling API models.
 *
 * @author Vladislav.Soroka
 * @since 10/14/13
 */
@Order(ExternalSystemConstants.UNORDERED)
public class JavaEEGradleProjectResolverExtension extends AbstractProjectResolverExtension {
  private static final Logger LOG = Logger.getInstance(JavaEEGradleProjectResolverExtension.class);

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull final DataNode<ModuleData> ideModule) {
    final WebConfiguration webConfiguration = resolverCtx.getExtraProject(gradleModule, WebConfiguration.class);

    final NotNullLazyValue<DataNode<? extends ModuleData>> findTargetModuleNode = new NotNullLazyValue<DataNode<? extends ModuleData>>() {
      @NotNull
      @Override
      protected DataNode<? extends ModuleData> compute() {
        final String mainSourceSetModuleId = ideModule.getData().getId() + ":main";
        DataNode<? extends ModuleData> targetModuleNode =
          ExternalSystemApiUtil.find(ideModule, GradleSourceSetData.KEY, new BooleanFunction<DataNode<GradleSourceSetData>>() {
            @Override
            public boolean fun(DataNode<GradleSourceSetData> node) {
              return mainSourceSetModuleId.equals(node.getData().getId());
            }
          });
        if (targetModuleNode == null) {
          targetModuleNode = ideModule;
        }
        return targetModuleNode;
      }
    };
    if (webConfiguration != null) {
      final List<War> warModels = ContainerUtil.map(webConfiguration.getWarModels(), new Function<WebConfiguration.WarModel, War>() {
        @Override
        public War fun(WebConfiguration.WarModel model) {
          War war = new War(model.getWarName(), model.getWebAppDirName(), model.getWebAppDir());
          war.setWebXml(model.getWebXml());
          war.setWebResources(mapWebResources(model.getWebResources()));
          war.setClasspath(model.getClasspath());
          war.setManifestContent(model.getManifestContent());
          return war;
        }
      });
      findTargetModuleNode.getValue().createChild(
        WebConfigurationModelData.KEY, new WebConfigurationModelData(GradleConstants.SYSTEM_ID, warModels));
    }

    final EarConfiguration earConfiguration = resolverCtx.getExtraProject(gradleModule, EarConfiguration.class);
    if (earConfiguration != null) {
      final List<Ear> warModels = ContainerUtil.map(earConfiguration.getEarModels(), new Function<EarConfiguration.EarModel, Ear>() {
        @Override
        public Ear fun(EarConfiguration.EarModel model) {
          Ear ear = new Ear(model.getEarName(), model.getAppDirName(), model.getLibDirName());
          ear.setManifestContent(model.getManifestContent());
          ear.setDeploymentDescriptor(model.getDeploymentDescriptor());
          ear.setResources(mapEarResources(model.getResources()));
          return ear;
        }
      });

      final Collection<DependencyData> deployDependencies =
        GradleProjectResolverUtil.getIdeDependencies(findTargetModuleNode.getValue(), earConfiguration.getDeployDependencies());
      final Collection<DependencyData> earlibDependencies =
        GradleProjectResolverUtil.getIdeDependencies(findTargetModuleNode.getValue(), earConfiguration.getEarlibDependencies());

      findTargetModuleNode.getValue().createChild(
        EarConfigurationModelData.KEY,
        new EarConfigurationModelData(GradleConstants.SYSTEM_ID, warModels, deployDependencies, earlibDependencies));
    }
    nextResolver.populateModuleExtraModels(gradleModule, ideModule);
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    return ContainerUtil.<Class>set(WebConfiguration.class, EarConfiguration.class);
  }

  private static List<WebResource> mapWebResources(List<WebConfiguration.WebResource> webResources) {
    return ContainerUtil.mapNotNull(webResources, new Function<WebConfiguration.WebResource, WebResource>() {
      @Override
      public WebResource fun(WebConfiguration.WebResource resource) {
        if (resource == null) return null;

        final WarDirectory warDirectory = WarDirectory.fromPath(resource.getWarDirectory());
        return new WebResource(warDirectory, resource.getRelativePath(), resource.getFile());
      }
    });
  }

  private static List<EarResource> mapEarResources(List<EarConfiguration.EarResource> resources) {
    return ContainerUtil.mapNotNull(resources, new Function<EarConfiguration.EarResource, EarResource>() {
      @Override
      public EarResource fun(EarConfiguration.EarResource resource) {
        if (resource == null) return null;

        return  new EarResource(resource.getEarDirectory(), resource.getRelativePath(), resource.getFile());
      }
    });
  }

}
