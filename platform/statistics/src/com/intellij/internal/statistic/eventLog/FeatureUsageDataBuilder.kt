// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

/**
 * Mark the methods of FeatureUsageData that add new fields. Annotation is used to facilitate analysis.
 * @param additionalDataFields - an array of event data fields, that the method adds to FeatureUsageData.
 * You can describe validation rules for each field as "&lt;fieldName&gt;:&lt;validationRule&gt;". For example, "count:regexp#integer".
 * If you can't describe validation rule, you can pass value index in the signature of factory method,
 * starts at 0 ("&lt;fieldName&gt;::&lt;valueIndex&gt;"). For example, "count::4"
 *
 * @see com.intellij.internal.statistic.eventLog.FeatureUsageData.addCount
 * @see com.intellij.internal.statistic.eventLog.FeatureUsageData.addValue
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class FeatureUsageDataBuilder(val additionalDataFields: Array<String> = [])