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
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.WarModel;
import org.jetbrains.plugins.gradle.model.data.WarModelData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

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
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    WarModel warModel = resolverCtx.getExtraProject(gradleModule, WarModel.class);
    if (warModel != null) {
      WarModelData warModelData = new WarModelData(GradleConstants.SYSTEM_ID, warModel.getWebAppDirName(), warModel.getWebAppDir());
      warModelData.setWebXml(warModel.getWebXml());
      warModelData.setWebRoots(warModel.getWebRoots());
      warModelData.setClasspath(warModel.getClasspath());
      warModelData.setManifestContent(warModel.getManifestContent());
      ideModule.createChild(WarModelData.KEY, warModelData);
    }

    nextResolver.populateModuleExtraModels(gradleModule, ideModule);
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    return ContainerUtil.<Class>set(WarModel.class);
  }
}
