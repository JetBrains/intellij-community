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
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

public interface SpockConstants {

  @NonNls String SETUP_METHOD_NAME = "setup";
  @NonNls String CLEANUP_METHOD_NAME = "cleanup";
  @NonNls String SETUP_SPEC_METHOD_NAME = "setupSpec";
  @NonNls String CLEANUP_SPEC_METHOD_NAME = "cleanupSpec";

  Set<String> FIXTURE_METHOD_NAMES = ContainerUtil.immutableSet(
    SETUP_METHOD_NAME,
    CLEANUP_METHOD_NAME,
    SETUP_SPEC_METHOD_NAME,
    CLEANUP_SPEC_METHOD_NAME
  );

  Set<String> FEATURE_METHOD_LABELS = ContainerUtil.immutableSet(
    "and", "setup", "given", "expect", "when", "then", "cleanup", "where"
  );
}
