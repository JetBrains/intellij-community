// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

/**
 * Mark the methods that create or modify FeatureUsageData. Annotation is used to facilitate analysis.
 * @param dataIndex - FeatureUsageData index in the signature of factory method, starts at 0
 * @param eventIdIndex - event id index in the signature of factory method, starts at 0
 * @param additionalDataFields - an array of event data fields, that the factory method adds to FeatureUsageData.
 * You can describe validation rules for each field as "&lt;fieldName&gt;:&lt;validationRule&gt;". For example, "count:regexp#integer"
 *
 * @see newBooleanMetric
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class StatisticsEventProvider(val dataIndex: Int = -1,
                                         val eventIdIndex: Int = -1,
                                         val additionalDataFields: Array<String> = [])
