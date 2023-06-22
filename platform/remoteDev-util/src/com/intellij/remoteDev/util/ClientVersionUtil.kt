package com.intellij.remoteDev.util

import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ClientVersionUtil {
  fun isJBCSeparateConfigSupported(clientVersion: String) = VersionComparatorUtil.compare(clientVersion, "233.173") >= 0
}