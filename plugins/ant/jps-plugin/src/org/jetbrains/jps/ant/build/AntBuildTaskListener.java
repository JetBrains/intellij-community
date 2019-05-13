/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.ant.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;

import java.util.List;

/**
 * Register implementation of this class in META-INF/services. It will be called before starting an Ant pre/post processing task
 *
 * @author nik
 */
public abstract class AntBuildTaskListener {
  public abstract void beforeAntBuildTaskStarted(@NotNull JpsAntArtifactExtension extension, @NotNull List<String> vmParams, @NotNull List<String> programParams);
}
