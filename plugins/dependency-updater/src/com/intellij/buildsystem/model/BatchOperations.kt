package com.intellij.buildsystem.model

fun <T : OperationItem> Iterable<T>.performOperationOnEach(operationType: OperationType, operation: (T) -> Unit) =
    fold(mutableListOf<OperationFailure<T>>()) { failures, item ->
        @Suppress("TooGenericExceptionCaught") // Guarding against random runtime errors
        try {
            operation(item)
        } catch (e: Exception) {
            failures += OperationFailure(operationType, item, e)
        }

        failures
    }
