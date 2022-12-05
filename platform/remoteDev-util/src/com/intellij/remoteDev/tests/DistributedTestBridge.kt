package com.intellij.remoteDev.tests

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Connects different IDE agents during test session
 */
@ApiStatus.Internal
interface DistributedTestBridge {

  companion object {
    fun getInstance(project: Project) : DistributedTestBridge {
      return project.getService(DistributedTestBridge::class.java)
    }
  }

  /**
   * This method sends calls into every connected protocol to ensure all events which
   *  this process sent into protocol were successfully received on the other side
   * Use this method after a test to preserve correct order of messages
   *  in protocol `IDE` <-> `IDE` because test framework works via
   *  different protocol `IDE` <-> `Test Process`
   */
  fun syncProtocolEvents()

}