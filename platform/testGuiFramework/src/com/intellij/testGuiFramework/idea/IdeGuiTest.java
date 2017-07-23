/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.idea;

import com.intellij.openapi.projectRoots.JavaSdkVersion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_6;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdeGuiTest {
  boolean closeProjectBeforeExecution() default true;
  int retryCount() default 0;
  JavaSdkVersion runWithMinimumJdkVersion() default JDK_1_6;
}
