// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

public final class JpsIdeaSdkType extends JpsSdkType<JpsSimpleElement<JpsIdeaSdkProperties>> implements JpsJavaSdkTypeWrapper {
  public static final JpsIdeaSdkType INSTANCE = new JpsIdeaSdkType();

  @Override
  public String getJavaSdkName(@NotNull JpsElement properties) {
    return ((JpsIdeaSdkProperties)((JpsSimpleElement<?>)properties).getData()).getJdkName();
  }

  @Override
  public String getPresentableName() {
    return "IntelliJ Platform Plugin SDK";
  }
}
