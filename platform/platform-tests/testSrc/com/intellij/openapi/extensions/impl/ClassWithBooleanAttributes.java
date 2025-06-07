// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.util.xmlb.annotations.Attribute;


public class ClassWithBooleanAttributes {
  @Attribute("implementationClass") String implementationClass;
  @Attribute("cleanupTool") boolean cleanupTool;
  @Attribute("isInternal") boolean isInternal;
}
