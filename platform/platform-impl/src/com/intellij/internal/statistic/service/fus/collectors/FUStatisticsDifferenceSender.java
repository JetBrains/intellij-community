// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

/**
 * Implement this markup interface in {@link FeatureUsagesCollector}'s implementation to post "difference" value metrics.
 * For such collectors we persist sent data (between send sessions)
 * and merge metrics (actualValue = actualValueFromCollector - persistedValue).
 * "difference" value example: we want to know "how many times MyAction was invoked".
 * <ol>
 * <li> my.foo.MyCollector(implements FUStatisticsDifferenceSender) calculates
 * common invocations and returns "myAction.invokes"=N where N is total invocations count.
 * <li> First send: action was totally invoked 17 times. my.foo.MyCollector returns 17.
 * We send "myAction.invokes"=17.
 * <li> Second send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
 * Action was invoked 13 times from the previous send.
 * We send "myAction.invokes"=13.
 * <li> Third send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
 * Action was not invoked from the previous send. We send NOTHING.
 * </ol>
 */
public interface FUStatisticsDifferenceSender {
}
