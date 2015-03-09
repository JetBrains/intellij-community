/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.IdeaXml;

import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 11/8/12
 */
public abstract class AbstractIdeaSpecificSettings<T, C, SdkType> {
  public void readIdeaSpecific(@NotNull Element root, T model, @Nullable SdkType projectSdkType, @Nullable Map<String, String> levels) {
    expandElement(root, model);

    readLanguageLevel(root, model);

    setupCompilerOutputs(root, model);
    readContentEntry(root, model);

    setupJdk(root, model, projectSdkType);
    setupLibraryRoots(root, model);
    overrideModulesScopes(root, model);
    if (levels != null) {
      readLibraryLevels(root, levels);
    }
  }

  public void initLevels(final Element root, T model, Map<String, String> levels) throws InvalidDataException {
    expandElement(root, model);
    readLanguageLevel(root, model);
    readLibraryLevels(root, levels);
  }

  public void updateEntries(Element root, T model, @Nullable SdkType projectSdkType) {
    setupJdk(root, model, projectSdkType);
    setupCompilerOutputs(root, model);
    readContentEntry(root, model);
  }

  private void readContentEntry(Element root, T model) {
    List<Element> entriesElements = root.getChildren(IdeaXml.CONTENT_ENTRY_TAG);
    if (entriesElements.isEmpty()) {
      // todo
      C[] entries = getEntries(model);
      if (entries.length > 0) {
        readContentEntry(root, entries[0], model);
      }
    }
    else {
      for (Element element : entriesElements) {
        readContentEntry(element, createContentEntry(model, element.getAttributeValue(IdeaXml.URL_ATTR)), model);
      }
    }
  }

  protected void readLibraryLevels(Element root, @NotNull Map<String, String> levels) {
  }

  protected abstract C[] getEntries(T model);

  protected abstract C createContentEntry(T model, String url);

  protected abstract void setupLibraryRoots(Element root, T model);

  protected abstract void setupJdk(Element root, T model, @Nullable SdkType projectSdkType);

  protected abstract void setupCompilerOutputs(Element root, T model);

  protected abstract void readLanguageLevel(Element root, T model);

  protected abstract void expandElement(Element root, T model);

  protected abstract void overrideModulesScopes(Element root, T model);

  public abstract void readContentEntry(Element root, C entry, T model);
}
