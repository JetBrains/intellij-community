package com.intellij.grazie.cloud

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.APP)
@ApiStatus.Internal
class GrazieCloudConnectionState: Disposable {
  enum class ConnectionState {
    Stable,
    Error
  }

  private var errors: Set<BackgroundCloudService> = setOf()
  private val mayRetryGecAt = AtomicLong(0)

  fun interface Listener {
    fun cloudConnectionStateChanged(state: ConnectionState)

    companion object {
      @Topic.AppLevel
      val TOPIC = Topic(Listener::class.java)
    }
  }

  override fun dispose() {
  }

  init {
    application.addApplicationListener(object : ApplicationListener {
      override fun writeActionFinished(action: Any) {
        mayRetryGecAt.set(0)
      }
    }, this)
  }

  @Synchronized
  private fun changeState(change: (Set<BackgroundCloudService>) -> Set<BackgroundCloudService>): Set<BackgroundCloudService> {
    errors = change(errors)
    return errors
  }

  companion object {
    fun stateChanged(state: ConnectionState, cloudService: BackgroundCloudService): ConnectionState {
      val service = getInstance()
      val error = state == ConnectionState.Error
      if (error && cloudService == BackgroundCloudService.GEC) {
        service.mayRetryGecAt.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(15))
      }
      val before = toState(service.errors)
      val after = toState(service.changeState { if (error) it + setOf(cloudService) else it - setOf(cloudService) })
      if (before != after) {
        application.messageBus.syncPublisher(Listener.TOPIC).cloudConnectionStateChanged(after)
      }
      return after
    }

    fun obtainCurrentState(): ConnectionState = toState(getInstance().errors)

    private fun toState(errors: Set<BackgroundCloudService>) =
      if (errors.isEmpty()) ConnectionState.Stable else ConnectionState.Error

    @JvmStatic
    fun isAfterRecentGecError(): Boolean {
      val service = getInstance()
      return BackgroundCloudService.GEC in service.errors && System.nanoTime() < service.mayRetryGecAt.get()
    }

    private fun getInstance() = service<GrazieCloudConnectionState>()
  }
}
