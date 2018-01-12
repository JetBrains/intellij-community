/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


package com.intellij.internal.statistic.eventLog

import com.intellij.util.containers.ContainerUtil

open class LogEvent(@Transient var recorderId: String, @Transient var userUid: String, sessionId: String, type: String) {
    @Transient var timestamp = System.currentTimeMillis()
    @Transient var sessionUid: String = sessionId
    @Transient var actionType: String = type
    @Transient var data: MutableMap<String, String> = ContainerUtil.newHashMap()

    fun shouldMerge(next: LogEvent): Boolean {
        if (actionType != next.actionType) return false
        if (recorderId != next.recorderId) return false
        if (userUid != next.userUid) return false
        if (sessionUid != next.sessionUid) return false
        if (data != next.data) return false
        return true
    }
}