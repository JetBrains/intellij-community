package com.intellij.grazie.cloud

/**
 * The cloud services that are constantly running in the background.
 * If a connectivity issue or a server error occurs, it's likely to persist for some time.
 * So it should be reported to the user and the future requests possibly suppressed.
 */
enum class BackgroundCloudService {
  GEC, NLC
}