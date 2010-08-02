/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.config.ExternalizablePropertyContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AntConfigurationBase extends AntConfiguration {

  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();

  protected AntConfigurationBase(final Project project) {
    super(project);
  }

  public static AntConfigurationBase getInstance(final Project project) {
    return (AntConfigurationBase)AntConfiguration.getInstance(project);
  }

  public abstract boolean isFilterTargets();

  public abstract void setFilterTargets(final boolean value);

  public abstract List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target);

  @Nullable
  public abstract AntBuildTarget getTargetForEvent(final ExecutionEvent event);

  public abstract void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event);

  public abstract void clearTargetForEvent(final ExecutionEvent event);

  public abstract boolean isAutoScrollToSource();

  public abstract void setAutoScrollToSource(final boolean value);

  public abstract AntInstallation getProjectDefaultAnt();

  public ExternalizablePropertyContainer getProperties() {
    return myProperties;
  }

  public final void ensureInitialized() {
    int attemptCount = 0; // need this in order to make sure we will not block swing thread forever
    while (!isInitialized() && attemptCount < 6000) {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) {
      }
      attemptCount++;
    }
  }

  public abstract void setContextFile(@NotNull XmlFile file, @Nullable XmlFile context);

  @Nullable
  public abstract XmlFile getContextFile(@Nullable XmlFile file);
  
  @Nullable
  public abstract XmlFile getEffectiveContextFile(@Nullable XmlFile file);
}
