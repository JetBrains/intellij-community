// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.annotate

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import git4idea.annotate.GitAnnotationProvider.GitRawAnnotationProvider
import git4idea.telemetry.GitBackendTelemetrySpan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

@Service(Service.Level.PROJECT)
class GitAnnotationService(private val project: Project, private val cs: CoroutineScope) {

  companion object {
    private val LOG = logger<GitAnnotationService>()
  }

  @Throws(VcsException::class)
  fun annotate(root: VirtualFile,
               filePath: FilePath,
               revision: VcsRevisionNumber?,
               file: VirtualFile): GitFileAnnotation {
    return runBlockingCancellable {
      getInstance().getTracer(VcsScope).spanBuilder(GitBackendTelemetrySpan.Annotations.OpenAnnotation.getName()).use { span ->
        val providers = GitRawAnnotationProvider.EP_NAME.getExtensions(project)
        val default = providers.single { GitRawAnnotationProvider.isDefault(it.id) }

        if (providers.size == 1) {
          annotateWithSingleProvider(default, root, filePath, revision, file)
        }
        else {
          annotateWithSeveralProviders(providers, root, filePath, revision, file)
        }
      }
    }
  }

  private suspend fun annotateWithSingleProvider(provider: GitRawAnnotationProvider,
                                                 root: VirtualFile,
                                                 filePath: FilePath,
                                                 revision: VcsRevisionNumber?,
                                                 file: VirtualFile): GitFileAnnotation {
    return annotateWithProvider(provider, root, filePath, revision, file)
             .also { logAndReportAllResults(root, file, revision, listOf(it)) }
             .timedValue.value.getOrThrow() ?: throw noDefaultAnnotationException()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun annotateWithSeveralProviders(providers: List<GitRawAnnotationProvider>,
                                                   root: VirtualFile,
                                                   filePath: FilePath,
                                                   revision: VcsRevisionNumber?,
                                                   file: VirtualFile): GitFileAnnotation {
    require(providers.isNotEmpty()) // to guarantee that there is at least one `send` to the channel below

    val channel = cs.produce {
      for (provider in providers) {
        launch {
          send(annotateWithProvider(provider, root, filePath, revision, file))
        }
      }
    }

    val result = CompletableDeferred<GitFileAnnotation>()

    cs.launch {
      val received = mutableListOf<ProviderResult>()

      for (providerResult in channel) {
        received.add(providerResult)

        val value = providerResult.timedValue.value.getOrNull()
        if (value != null && result.complete(value)) {
          LOG.debug("Annotations was loaded using ${providerResult.provider.presentableName()}")
          logFirstResult(file, revision, providerResult)
        }
      }

      if (!result.isCompleted) {
        result.completeExceptionally(
          // the default provider cannot provide `null` annotation, and hence it threw an exception, rethrow it here
          received.first { it.byDefaultProvider() }.timedValue.value.exceptionOrNull() ?: noDefaultAnnotationException()
        )
      }

      logAndReportAllResults(root, file, revision, received)
    }

    return result.await()
  }

  private suspend fun annotateWithProvider(provider: GitRawAnnotationProvider,
                                           root: VirtualFile,
                                           filePath: FilePath,
                                           revision: VcsRevisionNumber?,
                                           file: VirtualFile): ProviderResult {
    return ProviderResult(
      provider,
      measureTimedValue {
        runCatching { coroutineToIndicator { provider.annotate(project, root, filePath, revision, file) } }
          .onFailure {
            if (it is ProcessCanceledException || it is CancellationException) {
              throw it
            }
          }
      }
    )
  }

  private fun noDefaultAnnotationException(): IllegalStateException {
    val defaultProvider = GitRawAnnotationProvider.EP_NAME.getExtensions(project).single { GitRawAnnotationProvider.isDefault(it.id) }

    return IllegalStateException(
      "There is a misconfiguration, at least ${defaultProvider.presentableName()} should provide annotation"
    )
  }

  private fun logFirstResult(file: VirtualFile, revision: VcsRevisionNumber?, first: ProviderResult) {
    LOG.debug {
      "First result for ${file.path} at ${revision?.asString()} is provided by ${first.provider.presentableName()} in ${first.timedValue.duration}"
    }
  }

  private suspend fun logAndReportAllResults(root: VirtualFile,
                                             file: VirtualFile,
                                             revision: VcsRevisionNumber?,
                                             results: List<ProviderResult>) {
    logAllExceptions(results)
    reportAllResults(root, file, revision, results)

    LOG.debug {
      results.joinToString(prefix = "Results for ${file.path} at ${revision?.asString()}: ") {
        val provider = it.provider.presentableName()
        val duration = it.timedValue.duration

        if (it.isAnnotationProvided()) {
          "got data from $provider in $duration"
        }
        else {
          "no data from $provider in $duration"
        }
      }
    }
  }

  private fun GitRawAnnotationProvider.presentableName() = javaClass.simpleName

  private fun logAllExceptions(results: List<ProviderResult>) {
    val success = results.any { it.isAnnotationProvided() }

    for (providerResult in results) {
      if (!success && providerResult.byDefaultProvider()) {
        // no need to log since the default provider exception is re-thrown in `annotate`
        continue
      }

      val exception = providerResult.timedValue.value.exceptionOrNull() ?: continue
      LOG.warn(exception)
    }
  }

  private suspend fun reportAllResults(root: VirtualFile,
                                       file: VirtualFile,
                                       revision: VcsRevisionNumber?,
                                       results: List<ProviderResult>) {
    GitAnnotationPerformanceListener.EP_NAME.extensionList.forEach { listener ->
      listener.onAnnotationFinished(
        project,
        root,
        file,
        revision,
        results.map { it.toAnnotationResult() }
      )
    }
  }

  private fun ProviderResult.toAnnotationResult(): GitAnnotationPerformanceListener.AnnotationResult {
    return GitAnnotationPerformanceListener.AnnotationResult(
      provider.id,
      timedValue.value.getOrNull(),
      timedValue.duration
    )
  }

  private data class ProviderResult(val provider: GitRawAnnotationProvider, val timedValue: TimedValue<Result<GitFileAnnotation?>>) {
    fun isAnnotationProvided(): Boolean {
      return timedValue.value.getOrNull() != null
    }

    fun byDefaultProvider(): Boolean {
      return GitRawAnnotationProvider.isDefault(provider.id)
    }
  }
}