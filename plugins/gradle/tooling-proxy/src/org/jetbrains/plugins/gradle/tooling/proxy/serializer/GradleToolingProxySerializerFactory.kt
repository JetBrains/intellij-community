// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy.serializer

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.intellij.gradle.toolingExtension.impl.model.dslBaseScriptModel.GradleDslBaseScriptModelHolder
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelId
import com.intellij.gradle.toolingExtension.modelAction.GradleBaseScriptModelFetchPhase
import com.intellij.gradle.toolingExtension.modelAction.GradleBuildFinishedModelFetchPhase
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelAction.GradleProjectLoadedModelFetchPhase
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.dsl.GradleDslBaseScriptModel
import org.gradle.tooling.model.dsl.GroovyDslBaseScriptModel
import org.gradle.tooling.model.dsl.KotlinDslBaseScriptModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultGradleLightBuild
import org.jetbrains.plugins.gradle.model.DefaultGradleLightProject
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalGradleEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalJavaEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGradleDslBaseScriptModel
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGroovyDslBaseScriptModel
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalKotlinDslBaseScriptModel

@ApiStatus.Internal
object GradleToolingProxySerializerFactory {

  fun getSerializer(classLoader: ClassLoader): GradleToolingProxySerializer {
    val kryo = getKryo(classLoader)
    kryo.registerGradleDslBaseScriptModelClasses()
    kryo.registerModelHolderClasses()
    kryo.registerBuildEnvironmentClasses()
    return KryoGradleToolingProxySerializer(kryo)
  }

  private fun Kryo.registerGradleDslBaseScriptModelClasses() {
    register(GradleDslBaseScriptModel::class.java)
    register(InternalGradleDslBaseScriptModel::class.java)
    register(GradleDslBaseScriptModelHolder::class.java)

    register(GroovyDslBaseScriptModel::class.java)
    register(InternalGroovyDslBaseScriptModel::class.java)

    register(KotlinDslBaseScriptModel::class.java)
    register(InternalKotlinDslBaseScriptModel::class.java)
  }

  private fun Kryo.registerModelHolderClasses() {
    register(GradleModelHolderState::class.java)

    register(GradleLightBuild::class.java)
    register(DefaultGradleLightBuild::class.java)

    register(GradleLightProject::class.java)
    register(DefaultGradleLightProject::class.java)

    register(ProjectIdentifier::class.java)
    register(GradleProjectIdentity::class.java)
    register(DefaultProjectIdentifier::class.java)
    register(DefaultBuildIdentifier::class.java)

    register(GradleModelId::class.java)
    register(GradleModelFetchPhase::class.java)
    register(GradleBaseScriptModelFetchPhase::class.java)
    register(GradleProjectLoadedModelFetchPhase::class.java)
    register(GradleBuildFinishedModelFetchPhase::class.java)
  }

  private fun Kryo.registerBuildEnvironmentClasses() {
    register(InternalBuildEnvironment::class.java, InternalBuildEnvironmentSerializer)
    register(InternalBuildIdentifier::class.java)
    register(InternalGradleEnvironment::class.java)
    register(InternalJavaEnvironment::class.java)
  }

  internal object InternalBuildEnvironmentSerializer : Serializer<InternalBuildEnvironment>(false) {

    override fun write(kryo: Kryo, output: Output, obj: InternalBuildEnvironment) {
      kryo.writeObject(output, obj.java)
      kryo.writeObject(output, obj.versionInfo)
      kryo.writeObject(output, obj.gradle)
      kryo.writeObject(output, obj.buildIdentifier)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out InternalBuildEnvironment>): InternalBuildEnvironment {
      val java = kryo.readObject(input, InternalJavaEnvironment::class.java)
      val versionInfo = kryo.readObject(input, String::class.java)
      val gradle = kryo.readObject(input, InternalGradleEnvironment::class.java)
      val buildIdentifier = kryo.readObject(input, InternalBuildIdentifier::class.java)
      return InternalBuildEnvironment(
        { buildIdentifier }, { gradle }, { java }, { versionInfo }
      )
    }
  }
}