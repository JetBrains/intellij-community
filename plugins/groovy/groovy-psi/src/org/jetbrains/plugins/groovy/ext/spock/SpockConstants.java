// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;

public interface SpockConstants {

  @NlsSafe String SETUP_METHOD_NAME = "setup";
  @NlsSafe String CLEANUP_METHOD_NAME = "cleanup";
  @NlsSafe String SETUP_SPEC_METHOD_NAME = "setupSpec";
  @NlsSafe String CLEANUP_SPEC_METHOD_NAME = "cleanupSpec";

  Set<String> FIXTURE_METHOD_NAMES = ContainerUtil.immutableSet(
    SETUP_METHOD_NAME,
    CLEANUP_METHOD_NAME,
    SETUP_SPEC_METHOD_NAME,
    CLEANUP_SPEC_METHOD_NAME
  );

  Set<@NlsSafe String> FEATURE_METHOD_LABELS = Set.of(
    "and", "setup", "given", "expect", "when", "then", "cleanup", "where"
  );
}
