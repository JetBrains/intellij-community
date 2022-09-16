// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import java.io.Serializable;
import java.util.List;

public interface DependencyAccessorsModel extends Serializable {

  List<String> getSources();

  List<String> getClasses();
}
