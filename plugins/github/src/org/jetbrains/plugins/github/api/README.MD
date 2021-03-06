#Github API HOW-TO

To invoke API calls you first need to acquire an instance of `org.jetbrains.plugins.github.api.GithubApiRequestExecutor`.
To do that you have to have a `org.jetbrains.plugins.github.authentication.accounts.GithubAccount` which will be used for operation.

###Preparations
To acquire account use `org.jetbrains.plugins.github.authentication.GithubAuthenticationManager` service.
To acquire executor use `org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager` service.
Both of these operation must be performed on EDT prior to API method execution (there are exception to this rule, but if you encounter one, it is strongly suggested to revise your code).

###Invoking the API
There are multiple utilities allowing you to call the API in a generic way:
* `org.jetbrains.plugins.github.api.GithubApiRequests`
* `org.jetbrains.plugins.github.api.GithubApiContentHelper`
* various subclasses inside `org.jetbrains.plugins.github.api.GithubApiRequest`

The most generic way could be found in the plugin code.
Simplest example:
```kotlin
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider

class MyService(private val executorManager: GithubApiRequestExecutorManager,
                private val accountInformationProvider: GithubAccountInformationProvider) {
  @CalledOnAwt
  fun getCurrentUserLogin(project: Project, account: GithubAccount) {
    val executor = executorManager.getExecutor(account, project) ?: return

    object : Task.Backgroundable(project, "Loading Username For $account") {
      private lateinit var username: String

      override fun run(indicator: ProgressIndicator) {
        //if you need cached information
        username = accountInformationProvider.getInformation(executor, indicator, account).login
        //if you need up-to-date information
        username = executor.execute(indicator, GithubApiRequests.CurrentUser.get(account.server)).login
      }

      override fun onSuccess() {
        //use username
      }

      override fun onThrowable(error: Throwable) {
        //handle error
      }
    }.queue()
  }
}
```
