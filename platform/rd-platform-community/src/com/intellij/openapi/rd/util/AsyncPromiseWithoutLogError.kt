package com.intellij.openapi.rd.util

import org.jetbrains.concurrency.AsyncPromise

class AsyncPromiseWithoutLogError<T> : AsyncPromise<T>() { override fun shouldLogErrors() = false }