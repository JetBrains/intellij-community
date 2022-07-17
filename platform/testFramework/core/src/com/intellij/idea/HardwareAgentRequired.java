// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.idea.extensions.HardwareAgentRequiredExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@ExtendWith(HardwareAgentRequiredExecutionCondition.class)
public @interface HardwareAgentRequired {
}