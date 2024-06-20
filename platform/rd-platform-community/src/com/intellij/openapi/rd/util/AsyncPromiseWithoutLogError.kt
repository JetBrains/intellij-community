package com.intellij.openapi.rd.util

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise

@ApiStatus.Internal
class AsyncPromiseWithoutLogError<T> : AsyncPromise<T>() { override fun shouldLogErrors() = false }