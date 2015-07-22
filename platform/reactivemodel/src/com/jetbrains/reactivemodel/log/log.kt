package com.jetbrains.reactivemodel.log

import java.util.concurrent.TimeUnit
import java.util.logging
import java.util.logging.Level

public object Logger {
    public val logger:java.util.logging.Logger = logging.Logger.getLogger(javaClass.getName())
}

public inline fun<T> catch(inline action: () -> T) : T {
    try {
        return action()
    } catch (e: Throwable) {
//        Logger.logger.log(Level.SEVERE, e, {""})
        e.printStackTrace()
    }
    return null
}

public inline fun logTime<T>(name: String, threshold: Long, f: () -> T) : T {
    val startTime = System.nanoTime()
    try {
        return f()
    } finally {
        val endTime = System.nanoTime()
        val tookTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)
        if (tookTime > threshold) {
            Logger.logger.log(Level.WARNING, "$name: $tookTime")
        }
    }

}