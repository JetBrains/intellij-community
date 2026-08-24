package com.intellij.driver.sdk.jdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

/**
 * An IDE-side [java.nio.file.Path].
 *
 * Obtain one with [remotePath]; the only value it carries back is [toString].
 */
@Remote("java.nio.file.Path")
interface RemotePath {
  override fun toString(): String
}

/**
 * `NioFiles.toPath` is the one always-present one-argument `String`-to-`Path` static the remote invoker can match.
 *
 * `Path.of` is unreachable: the invoker filters candidates by *exact* parameter count
 * (`Invoker.java:420-423`) and knows nothing about `isVarArgs`, so `Path.of(String, String...)` —
 * `parameterCount` 2 — can never be called with a single argument. See AT-5090.
 */
@Remote("com.intellij.openapi.util.io.NioFiles")
internal interface NioFiles {
  fun toPath(path: String): RemotePath?
}

/**
 * Converts [path] into an IDE-side [java.nio.file.Path], so a driver test can pass a path to platform API that takes one.
 *
 * Fails when the IDE cannot parse [path] as a path of its own file system.
 */
fun Driver.remotePath(path: String): RemotePath {
  return checkNotNull(utility(NioFiles::class).toPath(path)) {
    "'$path' is not a parseable path on the IDE side"
  }
}
