/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.builder;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SimpleFileStorage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.platform.loader.impl.repository.RepositoryConstants;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class RuntimeModuleDescriptorsBuilder extends TargetBuilder<BuildRootDescriptor, RuntimeModuleDescriptorsTarget> {
  private static final SimpleFileStorage.Provider<Integer> STORAGE_PROVIDER =
    new SimpleFileStorage.Provider<Integer>("project-configuration-hash.txt", EnumeratorIntegerDescriptor.INSTANCE);

  public RuntimeModuleDescriptorsBuilder() {
    super(Collections.singletonList(RuntimeModuleDescriptorsTarget.TARGET_TYPE));
  }

  @Override
  public void build(@NotNull RuntimeModuleDescriptorsTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, RuntimeModuleDescriptorsTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    JpsProject project = target.getProject();
    SimpleFileStorage<Integer> storage = context.getProjectDescriptor().dataManager.getStorage(target, STORAGE_PROVIDER);
    int hash = 0;
    for (JpsModule module : project.getModules()) {
      Collection<String> urls = JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly().classes().getUrls();
      hash = 31 * hash + urls.hashCode();
    }
    hash = hash * 1000 + RepositoryConstants.VERSION_NUMBER;
    if (Comparing.equal(hash, storage.getState())) {
      return;
    }

    RuntimeModuleDescriptorsGenerator.MessageHandler messageHandler = new RuntimeModuleDescriptorsGenerator.MessageHandler() {
      @Override
      public void showProgressMessage(String message) {
        context.processMessage(new ProgressMessage(message));
      }

      @Override
      public void reportError(String message) {
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR,
                                                   "Cannot generate dependencies information: " + StringUtil.decapitalize(message)));
      }
    };
    new RuntimeModuleDescriptorsGenerator(project, messageHandler).generateForDevelopmentMode();
    storage.setState(hash);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "IntelliJ runtime module descriptors builder";
  }
}
