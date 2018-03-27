/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.appengine.model.impl;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.jps.appengine.model.PersistenceApi;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineModuleExtensionProperties {
  @Tag("sdk-home-path")
  public String mySdkHomePath = "";

  @Tag("run-enhancer-on-make")
  public boolean myRunEnhancerOnMake = false;

  @XCollection(propertyElementName = "files-to-enhance", elementName = "file", valueAttributeName = "path")
  public List<String> myFilesToEnhance = new ArrayList<>();

  @Tag("persistence-api")
  public PersistenceApi myPersistenceApi = PersistenceApi.JDO;
}
