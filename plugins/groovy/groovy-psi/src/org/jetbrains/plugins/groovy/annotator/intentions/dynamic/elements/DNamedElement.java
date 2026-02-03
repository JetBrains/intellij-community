// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.openapi.util.NlsSafe;

public interface DNamedElement {

  @NlsSafe String getName();

  void setName(@NlsSafe String name);
}
