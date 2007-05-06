/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public interface AntFilesProvider {
  
  @NotNull
  List<File> getFiles();
}
