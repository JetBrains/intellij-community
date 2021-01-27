// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importer;

import com.intellij.application.options.ImportSchemeChooserDialog;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
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
  @Override
  public String @NotNull [] getSourceExtensions() {
    return new String[]{"xml"};
  }

  @Override
  public @Nullable CodeStyleScheme importScheme(@NotNull Project project,
                                                @NotNull VirtualFile selectedFile,
                                                @NotNull CodeStyleScheme currentScheme,
                                                @NotNull SchemeFactory<? extends CodeStyleScheme> schemeFactory) throws SchemeImportException {
    Pair<String, CodeStyleScheme> importPair =
      ImportSchemeChooserDialog.selectOrCreateTargetScheme(project, currentScheme, schemeFactory, readSchemeNames(selectedFile));
    if (importPair != null) {
      readFromStream(selectedFile, stream -> new EclipseCodeStyleImportWorker().importScheme(stream, importPair.first, importPair.second));
      return importPair.second;
    }
    return null;
  }

  private static @NlsSafe String [] readSchemeNames(VirtualFile selectedFile) throws SchemeImportException {
    Set<String> names = new HashSet<>();
    EclipseXmlProfileReader reader = new EclipseXmlProfileReader(new EclipseXmlProfileReader.OptionHandler() {
      @Override
      public void handleOption(@NotNull String eclipseKey, @NotNull String value) { }

      @Override
      public void handleName(String name) {
        names.add(name);
      }
    });
    readFromStream(selectedFile, stream -> reader.readSettings(stream));
    return ArrayUtil.toStringArray(names);
  }

  private static void readFromStream(VirtualFile file, ThrowableConsumer<InputStream, SchemeImportException> consumer) throws SchemeImportException {
    try (InputStream inputStream = file.getInputStream()) {
      consumer.consume(inputStream);
    }
    catch (IOException e) {
      throw new SchemeImportException(e);
    }
  }
}
