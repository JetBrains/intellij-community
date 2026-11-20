// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.annotate

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcsUtil.VcsUtil
import git4idea.annotate.GitAnnotationProvider.GitRawAnnotationProvider
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

@TestApplication
class GitAnnotationServiceTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `the first result cancels the other providers`(): Unit = timeoutRunBlocking {
    val annotation = createAnnotation()
    val slow = FakeProvider(DEFAULT_ID) { waitForCancel() }
    val fast = FakeProvider("fast") {
      // the providers run in parallel, so the slow one starts while the fast one waits
      waitFor(slow.started)
      annotation
    }
    mask(fast, slow)
    val reported = CompletableFuture<List<GitAnnotationPerformanceListener.AnnotationResult>>()
    ExtensionTestUtil.maskExtensions(GitAnnotationPerformanceListener.EP_NAME, listOf(object : GitAnnotationPerformanceListener {
      override suspend fun onAnnotationFinished(project: Project,
                                                root: VirtualFile,
                                                file: VirtualFile,
                                                revision: VcsRevisionNumber?,
                                                results: List<GitAnnotationPerformanceListener.AnnotationResult>) {
        reported.complete(results)
      }
    }), disposable)

    assertThat(annotate()).isSameAs(annotation)
    slow.cancelled.await()
    assertThat(reported.await().map { it.providerId }).containsExactly("fast")
  }

  @Test
  fun `the default provider answers when the other provider has no data`(): Unit = timeoutRunBlocking {
    val annotation = createAnnotation()
    mask(FakeProvider("fast") { null }, FakeProvider(DEFAULT_ID) { annotation })

    assertThat(annotate()).isSameAs(annotation)
  }

  @Test
  fun `a cancel of the caller cancels all providers`(): Unit = timeoutRunBlocking {
    val first = FakeProvider("first") { waitForCancel() }
    val second = FakeProvider(DEFAULT_ID) { waitForCancel() }
    mask(first, second)

    val job = launch { annotate() }
    first.started.await()
    second.started.await()
    job.cancelAndJoin()

    first.cancelled.await()
    second.cancelled.await()
  }

  @Test
  fun `the error of the default provider is rethrown when no provider has data`(): Unit = timeoutRunBlocking {
    mask(FakeProvider("fast") { null }, FakeProvider(DEFAULT_ID) { throw VcsException("no blame") })

    val error = assertThrows<VcsException> { annotate() }
    assertThat(error.message).isEqualTo("no blame")
  }

  private suspend fun annotate(): GitFileAnnotation = withContext(Dispatchers.IO) {
    val root = context.repo.root
    coroutineToIndicator {
      context.project.service<GitAnnotationService>().annotate(root, VcsUtil.getFilePath(root), null, root)
    }
  }

  private fun createAnnotation(): GitFileAnnotation {
    val root = context.repo.root
    return GitFileAnnotation(context.project, root, VcsUtil.getFilePath(root), root, null, emptyList())
  }

  private fun mask(vararg providers: GitRawAnnotationProvider) {
    @Suppress("UNCHECKED_CAST")
    val point = GitRawAnnotationProvider.EP_NAME.getPoint(context.project) as ExtensionPointImpl<GitRawAnnotationProvider>
    point.maskAll(providers.toList(), disposable, false)
  }

  private class FakeProvider(private val id: String, private val answer: () -> GitFileAnnotation?) : GitRawAnnotationProvider {
    val started = CompletableFuture<Unit>()
    val cancelled = CompletableFuture<Unit>()

    override fun getId(): String = id

    override fun annotate(project: Project, root: VirtualFile, filePath: FilePath, revision: VcsRevisionNumber?, file: VirtualFile): GitFileAnnotation? {
      started.complete(Unit)
      try {
        return answer()
      }
      catch (e: ProcessCanceledException) {
        cancelled.complete(Unit)
        throw e
      }
    }
  }

  companion object {
    private const val DEFAULT_ID = "default"

    private fun waitForCancel(): Nothing {
      waitFor(CompletableFuture<Unit>())
      error("the wait ends only with a cancel")
    }

    private fun waitFor(future: CompletableFuture<*>) {
      while (!future.isDone) {
        ProgressManager.checkCanceled()
        Thread.sleep(10)
      }
    }
  }
}
