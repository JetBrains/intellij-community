// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.DefaultExternalTask;
import org.jetbrains.plugins.gradle.model.ExternalTask;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.LinkageDetails;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class LinkageDetailsImpl implements LinkageDetails {
  private ExternalTask linkTask;
  private File outputLocation;
  private @NotNull List<String> additionalArgs;

  public LinkageDetailsImpl() {
    additionalArgs = Collections.emptyList();
  }

  public LinkageDetailsImpl(LinkageDetails details) {
    linkTask = new DefaultExternalTask(details.getLinkTask());
    outputLocation = details.getOutputLocation();
    additionalArgs = new ArrayList<>(details.getAdditionalArgs());
  }

  @Override
  public ExternalTask getLinkTask() {
    return linkTask;
  }

  public void setLinkTask(ExternalTask linkTask) {
    this.linkTask = linkTask;
  }

  @Override
  public File getOutputLocation() {
    return outputLocation;
  }

  public void setOutputLocation(File outputLocation) {
    this.outputLocation = outputLocation;
  }

  @Override
  public @NotNull List<String> getAdditionalArgs() {
    return additionalArgs;
  }

  public void setAdditionalArgs(@NotNull List<String> additionalArgs) {
    this.additionalArgs = additionalArgs;
  }
}
