// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.module.Module;
import com.intellij.usages.Usage;

public interface UsageInModule extends Usage {
  Module getModule();
}
