package com.jetbrains.reactivemodel.log

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