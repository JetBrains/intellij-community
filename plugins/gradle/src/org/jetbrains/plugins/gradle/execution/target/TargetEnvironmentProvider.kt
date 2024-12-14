// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.*
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.value.DeferredLocalTargetValue
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.lang.LangCoreBundle
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathMappingSettings
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.nio.file.Paths

internal class TargetEnvironmentProvider {

  private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

  private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
  private val dependingOnEnvironmentPromise = mutableListOf<Promise<Unit>>()
  private val uploads = mutableListOf<Upload>()
  val pathMappingSettings = PathMappingSettings()

  fun supplyEnvironmentAndRunHandlers(
    targetEnvironment: TargetEnvironment,
    progressIndicator: GradleServerProgressIndicator,
  ) {
    environmentPromise.setResult(targetEnvironment to progressIndicator)
    for (promise in dependingOnEnvironmentPromise) {
      progressIndicator.checkCanceled()
      promise.blockingGet(0)  // Just rethrows errors.
    }
    dependingOnEnvironmentPromise.clear()
  }

  fun uploadVolumes(progressIndicator: GradleServerProgressIndicator) {
    for (upload in uploads) {
      progressIndicator.checkCanceled()
      upload.volume.upload(upload.relativePath, progressIndicator)
    }
    uploads.clear()
  }

  fun upload(
    uploadRoot: TargetEnvironment.UploadRoot,
    uploadPathString: String,
    uploadRelativePath: String,
  ): TargetValue<String> {
    val result = DeferredTargetValue(uploadPathString)
    doWhenEnvironmentPrepared { environment, progress ->
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      val resolvedTargetPath = volume.resolveTargetPath(uploadRelativePath)
      volume.upload(uploadRelativePath, progress)
      result.resolve(resolvedTargetPath)
      pathMappingSettings.addMapping(uploadPathString, resolvedTargetPath)
    }
    return result
  }

  fun requestUploadIntoTarget(
    path: String,
    request: TargetEnvironmentRequest,
    environmentConfiguration: TargetEnvironmentConfiguration,
  ): TargetValue<String> {
    val uploadPath = Paths.get(FileUtilRt.toSystemDependentName(path))
    val localRootPath = uploadPath.parent

    val languageRuntime = environmentConfiguration.runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java)
    val toolingFileOnTarget = LanguageRuntimeType.VolumeDescriptor(LanguageRuntimeType.VolumeType("gradleToolingFilesOnTarget"),
                                                                   "", "", "", "")
    val uploadRoot = languageRuntime?.createUploadRoot(toolingFileOnTarget, localRootPath) ?: TargetEnvironment.UploadRoot(localRootPath,
                                                                                                                           TargetEnvironment.TargetPath.Temporary())
    request.uploadVolumes += uploadRoot
    val result = DeferredTargetValue(path)
    doWhenEnvironmentPrepared(result::stopProceeding) { environment, targetProgressIndicator ->
      val volume = environment.uploadVolumes.getValue(uploadRoot)
      try {
        val relativePath = uploadPath.fileName.toString()
        val resolvedTargetPath = volume.resolveTargetPath(relativePath)
        uploads.add(Upload(volume, relativePath))
        result.resolve(resolvedTargetPath)
      }
      catch (t: Throwable) {
        targetProgressIndicator.stopWithErrorMessage(
          LangCoreBundle.message("progress.message.failed.to.resolve.0.1", volume.localRoot, t.localizedMessage))
        result.resolveFailure(t)
      }
    }
    return result
  }

  fun requestPort(request: TargetEnvironmentRequest, targetPort: Int): TargetValue<Int> {
    val binding = TargetEnvironment.TargetPortBinding(null, targetPort)
    request.targetPortBindings.add(binding)
    val result = DeferredLocalTargetValue(targetPort)
    doWhenEnvironmentPrepared { environment, _ ->
      val localPort = environment.targetPortBindings[binding]?.localEndpoint?.port
      result.resolve(localPort)
    }
    return result
  }

  private fun doWhenEnvironmentPrepared(onCancel: () -> Unit = {}, block: (TargetEnvironment, TargetProgressIndicator) -> Unit) {
    dependingOnEnvironmentPromise += environmentPromise.then { (environment, progress) ->
      if (progress.isCanceled || progress.isStopped) {
        onCancel.invoke()
      }
      else {
        block(environment, progress)
      }
    }
  }
}