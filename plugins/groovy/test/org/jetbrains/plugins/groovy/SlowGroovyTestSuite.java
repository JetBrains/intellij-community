// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import org.jetbrains.plugins.groovy.util.AllTestsSuite;
import org.jetbrains.plugins.groovy.util.SlowPolicy;
import org.jetbrains.plugins.groovy.util.TestPackage;
import org.junit.runner.RunWith;

@RunWith(AllTestsSuite.class)
@TestPackage(value = "org.jetbrains.plugins.groovy", policy = SlowPolicy.SLOW_ONLY)
public class SlowGroovyTestSuite {
}
