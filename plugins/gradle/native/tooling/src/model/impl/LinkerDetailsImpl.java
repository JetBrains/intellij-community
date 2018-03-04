// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.LinkerDetails;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public class LinkerDetailsImpl implements LinkerDetails {
  private final String myLinkTaskName;
  private final File myOutputFile;

  public LinkerDetailsImpl(String linkTaskName, File outputFile) {
    myLinkTaskName = linkTaskName;
    myOutputFile = outputFile;
  }

  public LinkerDetailsImpl(LinkerDetails details) {
    this(details.getLinkTaskName(), details.getOutputFile());
  }

  @Override
  public File getOutputFile() {
    return myOutputFile;
  }

  @Override
  public String getLinkTaskName() {
    return myLinkTaskName;
  }
}
