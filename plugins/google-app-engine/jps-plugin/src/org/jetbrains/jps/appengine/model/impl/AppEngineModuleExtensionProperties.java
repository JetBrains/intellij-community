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
