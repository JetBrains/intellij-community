/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.ant.model.impl.artifacts;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AntArtifactExtensionProperties {
  @Tag("file")
  public String myFileUrl;

  @Tag("target")
  public String myTargetName;

  @Attribute("enabled")
  public boolean myEnabled;

  @XCollection(propertyElementName = "build-properties")
  public List<BuildFileProperty> myUserProperties = new ArrayList<>();
}
