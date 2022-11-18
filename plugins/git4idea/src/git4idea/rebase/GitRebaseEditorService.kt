// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.ide.XmlRpcServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Pair
import git4idea.commands.GitHandler
import git4idea.commands.GitScriptGenerator
import git4idea.config.GitExecutable
import git4idea.editor.GitRebaseEditorApp
import git4idea.editor.GitRebaseEditorAppHandler
import org.jetbrains.ide.BuiltInServerManager
import java.io.File
import java.util.*

/**
 * The service that generates editor script for
 */
@Service(Service.Level.APP)
class GitRebaseEditorService : Disposable {
  /**
   * The lock object
   */
  private val myScriptLock = Any()

  /**
   * The handlers to use
   */
  private val myHandlers: MutableMap<UUID, Pair<GitRebaseEditorHandler, GitExecutable>> = HashMap()

  /**
   * The lock for the handlers
   */
  private val myHandlersLock = Any()

  private fun addInternalHandler() {
    val xmlRpcServer = XmlRpcServer.getInstance()
    if (!xmlRpcServer.hasHandler(GitRebaseEditorAppHandler.HANDLER_NAME)) {
      xmlRpcServer.addHandler(GitRebaseEditorAppHandler.HANDLER_NAME, InternalHandlerRebase())
    }
  }

  override fun dispose() {
    val xmlRpcServer = ApplicationManager.getApplication().getServiceIfCreated(XmlRpcServer::class.java)
    xmlRpcServer?.removeHandler(GitRebaseEditorAppHandler.HANDLER_NAME)
  }

  /**
   * Get editor command
   *
   * @return the editor command
   */
  @Synchronized
  fun getEditorCommand(executable: GitExecutable): String {
    synchronized(myScriptLock) {
      val port = BuiltInServerManager.getInstance().waitForStart().port
      return GitScriptGenerator(executable)
        .addParameters(Integer.toString(port))
        .commandLine(GitRebaseEditorApp::class.java, false)
    }
  }

  /**
   * Register the handler in the service
   *
   * @param handler the handler to register
   * @return the handler identifier
   */
  fun registerHandler(handler: GitHandler, editorHandler: GitRebaseEditorHandler): UUID {
    addInternalHandler()
    synchronized(myHandlersLock) {
      val key = UUID.randomUUID()
      myHandlers[key] = Pair.create(editorHandler, handler.executable)
      return key
    }
  }

  /**
   * Unregister handler
   *
   * @param handlerNo the handler number.
   */
  fun unregisterHandler(handlerNo: UUID) {
    synchronized(myHandlersLock) {
      val removed = myHandlers.remove(handlerNo)
      checkNotNull(removed) { "The handler $handlerNo has been already removed" }
    }
  }

  /**
   * Get handler
   *
   * @param handlerNo the handler number.
   */
  fun getHandler(handlerNo: UUID): Pair<GitRebaseEditorHandler, GitExecutable> {
    synchronized(myHandlersLock) {
      return myHandlers[handlerNo] ?: throw IllegalStateException("The handler $handlerNo is not registered")
    }
  }

  /**
   * The internal xml rcp handler
   */
  inner class InternalHandlerRebase : GitRebaseEditorAppHandler {
    override fun editCommits(handlerNo: String, path: String, workingDir: String): Int {
      val pair = getHandler(UUID.fromString(handlerNo))
      val executable = pair.second
      val editorHandler = pair.first

      val file = executable.convertFilePathBack(path, File(workingDir))

      return editorHandler.editCommits(file)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): GitRebaseEditorService = service<GitRebaseEditorService>()
  }
}