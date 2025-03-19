// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.random.nextInt

// run StressTestUtil with following arguments:
// com.intellij.openapi.vfs.newvfs.persistent.SampleUser com.intellij.openapi.vfs.newvfs.persistent.SampleApp 10 10

class SampleUser: User {
  override fun run(userAgent: UserAgent) {
    userAgent.runApplication { app ->
      try {
        app.appInput.write(ByteBuffer.allocate(4).putInt(userAgent.id).array())
        app.appInput.flush()
        val msgSizeArr = app.appOutput.readNBytes(4)
        check(msgSizeArr.size == 4)
        val msgSize = ByteBuffer.wrap(msgSizeArr).getInt()
        val msgArr = app.appOutput.readNBytes(msgSize)
        check(msgArr.size == msgSize)
        val msg = msgArr.decodeToString()
        if (msg == "Hello, ${userAgent.id}!") {
          userAgent.addInteractionResult(InteractionResult(true, "good!"))
        }
        else {
          userAgent.addInteractionResult(InteractionResult(false, "bad!", "id=${userAgent.id}, but msg=$msg"))
        }
      } catch (e: Throwable) {
        userAgent.addInteractionResult(InteractionResult(false, "unhandled exception", e.toString()))
        throw e
      }
    }
  }
}

class SampleApp: App {
  override fun run(appAgent: AppAgent) {
    val idArr = appAgent.input.readNBytes(4)
    check(idArr.size == 4)
    val id = ByteBuffer.wrap(idArr).getInt()
    val behave = Random.nextInt(1..10) != 10
    val msg = "Hello, ${if (behave) id else -id}!"
    appAgent.output.write(ByteBuffer.allocate(4).putInt(msg.encodeToByteArray().size).array())
    appAgent.output.write(msg.encodeToByteArray())
    appAgent.output.flush()
  }
}
