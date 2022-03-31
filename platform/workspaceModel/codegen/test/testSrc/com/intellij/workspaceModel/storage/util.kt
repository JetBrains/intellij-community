package com.intellij.workspaceModel.storage

import kotlin.test.assertTrue

fun <E> assertOneElement(collection: Collection<E>): E {
    assertTrue(collection.size == 1, "Collection size: ${collection.size}")
    return collection.single()
}