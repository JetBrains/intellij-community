// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/**
 * Log4j 1.2 contains security vulnerability (<a href="https://nvd.nist.gov/vuln/detail/CVE-2019-17571">CVE-2019-17571</a>) so
 * it shouldn't be used. For code in IntelliJ plugins it's recommended to use {@link com.intellij.openapi.diagnostic.Logger IntelliJ Logging API}
 * directly. 
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
package org.apache.log4j;

import org.jetbrains.annotations.ApiStatus;