// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import java.awt.Component
import java.util.*

/**
 * Allows to acquire API executor without exposing the auth token to external code
 */
class GithubApiRequestExecutorManager(private val authenticationManager: GithubAuthenticationManager,
                                      private val requestExecutorFactory: GithubApiRequestExecutor.Factory) {
  @CalledInAwt
  fun getExecutor(account: GithubAccount, project: Project): GithubApiRequestExecutor? {
    return authenticationManager.getOrRequestTokenForAccount(account, project)
      ?.let(requestExecutorFactory::create)
  }

  @CalledInAwt
  fun getManagedHolder(account: GithubAccount, project: Project): ManagedHolder? {
    val requestExecutor = getExecutor(account, project) ?: return null
    val holder = ManagedHolder(account, requestExecutor)
    ApplicationManager.getApplication().messageBus.connect(holder).subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, holder)
    return holder
  }

  @CalledInAwt
  fun getExecutor(account: GithubAccount, parentComponent: Component): GithubApiRequestExecutor? {
    return authenticationManager.getOrRequestTokenForAccount(account, null, parentComponent)
      ?.let(requestExecutorFactory::create)
  }

  @CalledInAwt
  @Throws(GithubMissingTokenException::class)
  fun getExecutor(account: GithubAccount): GithubApiRequestExecutor {
    return authenticationManager.getTokenForAccount(account)?.let(requestExecutorFactory::create)
           ?: throw GithubMissingTokenException(account)
  }

  inner class ManagedHolder internal constructor(private val account: GithubAccount, initialExecutor: GithubApiRequestExecutor)
    : Disposable, AccountTokenChangedListener {

    private var isDisposed = false
    var executor: GithubApiRequestExecutor = initialExecutor
      @CalledInAny
      get() {
        if (isDisposed) throw IllegalStateException("Already disposed")
        return field
      }
      @CalledInAwt
      internal set(value) {
        field = value
        eventDispatcher.multicaster.executorChanged()
      }

    private val eventDispatcher = EventDispatcher.create(ExecutorChangeListener::class.java)

    override fun tokenChanged(account: GithubAccount) {
      if (account == this.account) runInEdt {
        try {
          executor = getExecutor(account)
        }
        catch (e: GithubMissingTokenException) {
          //token is missing, so content will be closed anyway
        }
      }
    }

    fun addListener(listener: ExecutorChangeListener, disposable: Disposable) = eventDispatcher.addListener(listener, disposable)

    override fun dispose() {
      isDisposed = true
    }
  }

  interface ExecutorChangeListener : EventListener {
    fun executorChanged()
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubApiRequestExecutorManager = service()
  }
}