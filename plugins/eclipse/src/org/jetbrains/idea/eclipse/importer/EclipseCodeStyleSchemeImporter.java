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
package org.jetbrains.idea.eclipse.importer;


import com.intellij.application.options.ImportSchemeChooserDialog;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public class EclipseCodeStyleSchemeImporter implements SchemeImporter<CodeStyleScheme>, EclipseXmlProfileElements {
  @NotNull
  @Override
  public String[] getSourceExtensions() {
    return new String[]{"xml"};
  }

  @Nullable
  @Override
  public CodeStyleScheme importScheme(@NotNull Project project,
                                      @NotNull VirtualFile selectedFile,
                                      @NotNull CodeStyleScheme currentScheme,
                                      @NotNull SchemeFactory<CodeStyleScheme> schemeFactory) throws SchemeImportException {
    final Pair<String, CodeStyleScheme> importPair =
      ImportSchemeChooserDialog.selectOrCreateTargetScheme(project, currentScheme, schemeFactory, readSchemeNames(selectedFile));
    if (importPair != null) {
      readFromStream(selectedFile, stream -> new EclipseCodeStyleImportWorker().importScheme(stream, importPair.first, importPair.second));
      return importPair.second;
    }
    return null;
  }

  @Nullable
  @Override
  public String getAdditionalImportInfo(@NotNull CodeStyleScheme scheme) {
    return null;
  }

  /**
   * Attempts to read scheme names from the given stream. The stream may contain several schemes in which case all the available
   * names are returned.
   *
   * @return Either scheme name or null if the scheme doesn't have a name.
   * @throws SchemeImportException
   */
  @NotNull
  private static String[] readSchemeNames(@NotNull VirtualFile selectedFile) throws SchemeImportException {
    final Set<String> names = new HashSet<>();
    final EclipseXmlProfileReader reader = new EclipseXmlProfileReader(new EclipseXmlProfileReader.OptionHandler() {
      @Override
      public void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException {
        // Ignore
      }
      @Override
      public void handleName(String name) {
        names.add(name);
      }
    });
    readFromStream(selectedFile, stream -> reader.readSettings(stream));
    return ArrayUtil.toStringArray(names);
  }

  private static void readFromStream(@NotNull final VirtualFile file,
                                     @NotNull final ThrowableConsumer<InputStream, SchemeImportException> consumer)
    throws SchemeImportException {
    InputStream inputStream = null;
    try {
      inputStream = file.getInputStream();
      consumer.consume(inputStream);
    } catch (IOException e) {
      throw new SchemeImportException(e);
    }
    finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        }
        catch (IOException e) {
          //
        }
      }
    }
  }
}
