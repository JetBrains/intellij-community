// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.kotlin.dsl.InternalKotlinDslScriptsModel;

import java.io.IOException;

public class KotlinDslScriptsModelSerializationService implements SerializationService<KotlinDslScriptsModel> {
  @Override
  public byte[] write(KotlinDslScriptsModel object, Class<? extends KotlinDslScriptsModel> modelClazz) throws IOException {
    InternalKotlinDslScriptsModel internalKotlinDslScriptsModel = new InternalKotlinDslScriptsModel(object.getScriptModels());
    return new DefaultSerializationService().write(internalKotlinDslScriptsModel, modelClazz);
  }

  @Override
  public KotlinDslScriptsModel read(byte[] object, Class<? extends KotlinDslScriptsModel> modelClazz) throws IOException {
    return (KotlinDslScriptsModel)new DefaultSerializationService().read(object, modelClazz);
  }

  @Override
  public Class<? extends KotlinDslScriptsModel> getModelClass() {
    return KotlinDslScriptsModel.class;
  }
}
