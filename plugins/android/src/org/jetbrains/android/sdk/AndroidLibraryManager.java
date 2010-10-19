/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.android.util.OrderRoot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 19, 2009
 * Time: 6:15:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidLibraryManager {
  private final LibraryTable.ModifiableModel myModel;
  private final Map<Library, Library.ModifiableModel> myLibraryModels = new HashMap<Library, Library.ModifiableModel>();

  public AndroidLibraryManager(@NotNull LibraryTable.ModifiableModel model) {
    myModel = model;
  }

  @NotNull
  public LibraryTable.ModifiableModel getModel() {
    return myModel;
  }

  @NotNull
  public Map<Library, Library.ModifiableModel> getLibraryModels() {
    return myLibraryModels;
  }

  public void removeLibrary(@NotNull Library library) {
    myModel.removeLibrary(library);
  }

  public Library createNewAndroidPlatform(@NotNull IAndroidTarget target, @NotNull String sdkPath) {
    String libName = chooseNameForNewLibrary(target);
    if (myModel.getLibraryByName(libName) != null) {
      String newLibName;
      for (int i = 1; ; i++) {
        newLibName = libName + '(' + i + ')';
        if (myModel.getLibraryByName(newLibName) == null) break;
      }
      libName = newLibName;
    }
    Library library = myModel.createLibrary(libName);
    addJarsAndSources(library, target, sdkPath);
    return library;
  }

  private void addJarsAndSources(Library library, @NotNull IAndroidTarget target, @NotNull String sdkPath) {
    final Library.ModifiableModel model = getModifiableModelForLibrary(library);
    List<OrderRoot> rootsToAdd = AndroidSdkUtils.getLibraryRootsForTarget(target, sdkPath);
    for (OrderRoot root : rootsToAdd) {
      model.addRoot(root.getFile(), root.getType());
    }
  }

  public Library.ModifiableModel getModifiableModelForLibrary(Library library) {
    if (myModel instanceof LibrariesModifiableModel) {
      LibrariesModifiableModel m = (LibrariesModifiableModel)myModel;
      return m.getLibraryEditor(library).getModel();
    }
    Library.ModifiableModel libraryModel = myLibraryModels.get(library);
    if (libraryModel == null) {
      libraryModel = library.getModifiableModel();
      myLibraryModels.put(library, libraryModel);
    }
    return libraryModel;
  }

  private static String chooseNameForNewLibrary(IAndroidTarget target) {
    if (target instanceof AndroidTarget11) {
      return "Android SDK 1.1";
    }
    if (target.isPlatform()) {
      return target.getName() + " Platform";
    }
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return "Android " + parentTarget.getVersionName() + ' ' + target.getName();
    }
    return "Android " + target.getName();
  }

  public void apply() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Library library : myLibraryModels.keySet()) {
          Library.ModifiableModel model = myLibraryModels.get(library);
          model.commit();
        }
        if (myModel instanceof LibrariesModifiableModel) {
          myModel.commit();
        }
      }
    });
    myLibraryModels.clear();
  }
}
