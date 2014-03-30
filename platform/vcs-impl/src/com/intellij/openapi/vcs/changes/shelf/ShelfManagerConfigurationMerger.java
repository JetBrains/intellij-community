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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.XmlConfigurationMerger;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

public class ShelfManagerConfigurationMerger implements XmlConfigurationMerger {
  private final String myConfigPath;
  private final CompoundShelfFileProcessor myFileProcessor;

  public ShelfManagerConfigurationMerger() {
    myConfigPath = PathManager.getConfigPath()+ "/shelf";
    myFileProcessor = new CompoundShelfFileProcessor("shelf");
  }

  @TestOnly
  public ShelfManagerConfigurationMerger(final String configPath, @Nullable StreamProvider streamProvider) {
    myConfigPath = configPath;
    myFileProcessor = new CompoundShelfFileProcessor(streamProvider, configPath);
  }

  @Override
  @NotNull
  public Element merge(final Element serverElement, final Element localElement) {
    Map<Date, ShelvedChangeList> result = new LinkedHashMap<Date, ShelvedChangeList>();

    Map<String, ShelvedChangeList> serverFileToChangeList = collectChanges(serverElement);
    Map<String, ShelvedChangeList> localFileToChangeList = collectChanges(localElement);

    Collection<String> serverFileNames = myFileProcessor.getServerFiles();
    List<String> localFileNames = myFileProcessor.getLocalFiles();

    Collection<String> serverChangeListFiles = new HashSet<String>();

    for (String serverFileName : serverFileToChangeList.keySet()) {
      ShelvedChangeList changeList = serverFileToChangeList.get(serverFileName);
      final String newFileName;
      if (!localFileNames.contains(serverFileName)) {
        newFileName = myFileProcessor.copyFileFromServer(serverFileName, localFileNames);
      }
      else {
        ShelvedChangeList localChangeList = localFileToChangeList.get(serverFileName);
        if (localChangeList != null && localChangeList.DATE.equals(changeList.DATE)) {
          newFileName = myFileProcessor.copyFileFromServer(serverFileName, localFileNames);
        }
        else {
          newFileName = myFileProcessor.renameFileOnServer(serverFileName, serverFileNames, localFileNames );
        }
      }
      changeList.PATH = new File(myFileProcessor.getBaseIODir(),newFileName).getAbsolutePath();
      serverChangeListFiles.add(newFileName);
      result.put(changeList.DATE, changeList);      
    }

    for (ShelvedChangeList changeList : localFileToChangeList.values()) {
      result.put(changeList.DATE, changeList);
      serverChangeListFiles.remove(new File(changeList.PATH).getName());
    }

    Collection<ShelvedChangeList> resultChanges = result.values();

    for (ShelvedChangeList resultChange : resultChanges) {
      String patchFileName = new File(resultChange.PATH).getName();
      resultChange.PATH = myConfigPath + "/" + patchFileName;
      if (serverChangeListFiles.contains(patchFileName)) {
        for (ShelvedBinaryFile binaryFile : resultChange.getBinaryFiles()) {
          assert binaryFile.SHELVED_PATH != null;
          String binFileName = new File(binaryFile.SHELVED_PATH).getName();
          final String newBinFileName;
          if (localFileNames.contains(binFileName)) {
            newBinFileName = myFileProcessor.renameFileOnServer(binFileName, serverFileNames, localFileNames);
          }
          else {
            newBinFileName = myFileProcessor.copyFileFromServer(binFileName, localFileNames);
          }
          binaryFile.SHELVED_PATH = new File(myFileProcessor.getBaseIODir(),newBinFileName).getAbsolutePath();
        }
      }

    }

    Collection<ShelvedChangeList> changes = extractChanges(resultChanges, false);
    Collection<ShelvedChangeList> recycled = extractChanges(resultChanges, true);

    try {
      Element resultElement = new Element(localElement.getName());
      for (Object attrObject : localElement.getAttributes()) {
        Attribute attr = (Attribute)attrObject;
        resultElement.setAttribute(attr.getName(), attr.getValue());
      }
      ShelvedChangeList.writeChanges(changes, recycled, resultElement);
      return resultElement;
    }
    catch (WriteExternalException e) {
      return serverElement;
    }

  }

  private static Collection<ShelvedChangeList> extractChanges(final Collection<ShelvedChangeList> changes, final boolean recycled) {
    ArrayList<ShelvedChangeList> result = new ArrayList<ShelvedChangeList>();
    for (ShelvedChangeList change : changes) {
      if (change.isRecycled() == recycled) {
        result.add(change);
      }
    }
    return result;
  }

  private static Map<String, ShelvedChangeList> collectChanges(final Element original) {
    Map<String, ShelvedChangeList> result = new HashMap<String, ShelvedChangeList>();
    try {
      for (ShelvedChangeList shelvedChangeList : ShelvedChangeList.readChanges(original, true, false)) {
        result.put(new File(shelvedChangeList.PATH).getName(), shelvedChangeList);
      }
      for (ShelvedChangeList shelvedChangeList : ShelvedChangeList.readChanges(original, false, false)) {
        result.put(new File(shelvedChangeList.PATH).getName(), shelvedChangeList);
      }
    }
    catch (InvalidDataException e) {
      //ignore
    }

    return result;
  }

  @Override
  public String getComponentName() {
    return "ShelveChangesManager";
  }
}