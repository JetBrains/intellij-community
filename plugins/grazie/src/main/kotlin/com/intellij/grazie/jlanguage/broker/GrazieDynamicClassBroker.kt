// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage.broker

import com.intellij.grazie.GrazieDynamic
import org.languagetool.tools.classbroker.ClassBroker

object GrazieDynamicClassBroker : ClassBroker {
  override fun forName(qualifiedName: String): Class<*> {
    return GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
  }
}
