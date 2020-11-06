// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class BlockingExecutor : Executor {
  private val queue: BlockingQueue<Runnable> = ArrayBlockingQueue(100)

  override fun execute(action: Runnable?) {
    if (action == null) return
    queue.add(action)
  }

  fun run() {
    while (true) {
      val task = queue.poll(2, TimeUnit.SECONDS) ?: return
      task.run()
    }
  }
}