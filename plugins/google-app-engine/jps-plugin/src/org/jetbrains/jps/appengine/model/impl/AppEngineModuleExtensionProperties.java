package org.jetbrains.jps.appengine.model.impl;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
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

  @Tag("files-to-enhance")
  @AbstractCollection(surroundWithTag = false, elementTag = "file", elementValueAttribute = "path")
  public List<String> myFilesToEnhance = new ArrayList<String>();

  @Tag("persistence-api")
  public PersistenceApi myPersistenceApi = PersistenceApi.JDO;
}
