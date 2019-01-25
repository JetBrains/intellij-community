// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic

/**
 * @author Max Medvedev
 */
@CompileStatic
class GroovyLightProjectDescriptor {
  public static final LightProjectDescriptor GROOVY_LATEST = GroovyProjectDescriptors.GROOVY_LATEST
  public static final LightProjectDescriptor GROOVY_LATEST_REAL_JDK = GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK
}
