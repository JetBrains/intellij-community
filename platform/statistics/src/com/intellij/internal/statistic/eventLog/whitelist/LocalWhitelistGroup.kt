// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

class LocalWhitelistGroup @JvmOverloads constructor(
  var groupId: String,
  var useCustomRules: Boolean,
  var customRules: String = """{
  "event_id": [],
  "event_data": {}
}"""
)
