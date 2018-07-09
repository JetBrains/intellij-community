// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

// some FeatureUsagesCollector can implement markup interface FUStatisticsDifferenceSender.
// such collectors post "difference" value metrics.
// for such collectors we persist sent data(between send sessions)
// and merge metrics (actualValue = actualValueFromCollector - persistedValue)
// "difference" value example: we want to know "how many times MyAction was invoked".
//   1. my.foo.MyCollector(implements FUStatisticsDifferenceSender) calculates
//      common invocations and returns "myAction.invokes"=N where N is total invocations count.
//   2. first send: action was totally invoked 17 times. my.foo.MyCollector returns 17 .
//      we send "myAction.invokes"=17
//   3. second send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
//      action was invoked 13 times from the previous send.
//      we send "myAction.invokes"=13
//   4. third send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
//      action was not invoked from the previous send. we send NOTHING.
public interface FUStatisticsDifferenceSender {
}
