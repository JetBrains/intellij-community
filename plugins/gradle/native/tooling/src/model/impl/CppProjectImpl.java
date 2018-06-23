// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppBinary;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppProject;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.SourceFolder;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class CppProjectImpl implements CppProject {

  private final Set<SourceFolder> mySourceFolders = new LinkedHashSet<SourceFolder>();
  private final Set<CppBinary> binaries = new LinkedHashSet<CppBinary>();

  public CppProjectImpl() {
  }

  public CppProjectImpl(CppProject cppProject) {
    for (CppBinary binary : cppProject.getBinaries()) {
      addBinary(new CppBinaryImpl(binary));
    }
    for (SourceFolder sourceFolder : cppProject.getSourceFolders()) {
      addSourceFolder(new SourceFolderImpl(sourceFolder));
    }
  }

  @Override
  public Set<SourceFolder> getSourceFolders() {
    return Collections.unmodifiableSet(mySourceFolders);
  }

  public void addSourceFolder(SourceFolder folder) {
    mySourceFolders.add(folder);
  }

  @Override
  public Set<CppBinary> getBinaries() {
    return Collections.unmodifiableSet(binaries);
  }

  public void addBinary(CppBinary binary) {
    binaries.add(binary);
  }
}
