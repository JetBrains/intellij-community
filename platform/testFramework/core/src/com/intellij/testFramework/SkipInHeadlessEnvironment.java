/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.lang.annotation.*;

/**
 * Mark {@link com.intellij.testFramework.UsefulTestCase} implementations using this annotation if they require UI environment to run
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true", disabledReason = "Test is disabled in headless environment")
@Inherited
public @interface SkipInHeadlessEnvironment {
}
