// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkout

import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsKey
import git4idea.commands.GitCommandResult
import org.jetbrains.annotations.ApiStatus
import java.io.File

@ApiStatus.Experimental
interface GitCheckoutListener : CheckoutProvider.Listener {
  fun checkoutFailed(result: GitCommandResult?)

  companion object {
    @JvmStatic
    fun wrap(listener: CheckoutProvider.Listener) = object : GitCheckoutListener {
      override fun checkoutFailed(result: GitCommandResult?) {
      }

      override fun directoryCheckedOut(directory: File?, vcs: VcsKey?) {
        listener.directoryCheckedOut(directory, vcs)
      }

      override fun checkoutCompleted() {
        listener.checkoutCompleted()
      }
    }
  }
}