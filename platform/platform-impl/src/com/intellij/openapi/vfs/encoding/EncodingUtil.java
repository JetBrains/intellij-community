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
package com.intellij.openapi.vfs.encoding;

import com.intellij.AppTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class EncodingUtil {
  private static final String REASON_FILE_IS_A_DIRECTORY = "directory";
  private static final String REASON_BINARY_FILE = "binary";
  private static final String REASON_HARDCODED_IN_TEXT = "hard-coded";
  private static final String REASON_HARDCODED_FOR_FILE = "%s";

  enum Magic8 {
    ABSOLUTELY,
    WELL_IF_YOU_INSIST,
    NO_WAY
  }

  // check if file can be loaded in the encoding correctly:
  // returns true if bytes on disk, converted to text with the charset, converted back to bytes matched
  static Magic8 isSafeToReloadIn(@NotNull VirtualFile virtualFile, @NotNull String text, @NotNull byte[] bytes, @NotNull Charset charset) {
    // file has BOM but the charset hasn't
    byte[] bom = virtualFile.getBOM();
    if (bom != null && !CharsetToolkit.canHaveBom(charset, bom)) return Magic8.NO_WAY;

    // the charset has mandatory BOM (e.g. UTF-xx) but the file hasn't or has wrong
    byte[] mandatoryBom = CharsetToolkit.getMandatoryBom(charset);
    if (mandatoryBom != null && !ArrayUtil.startsWith(bytes, mandatoryBom)) return Magic8.NO_WAY;

    String loaded = LoadTextUtil.getTextByBinaryPresentation(bytes, charset).toString();

    String separator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
    String toSave = StringUtil.convertLineSeparators(loaded, separator);

    String failReason = LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile);
    if (failReason != null && CharsetToolkit.UTF8_CHARSET.equals(virtualFile.getCharset()) && !CharsetToolkit.UTF8_CHARSET.equals(charset)) {
      return Magic8.NO_WAY; // can't reload utf8-autodetected file in another charset
    }

    byte[] bytesToSave;
    try {
      bytesToSave = toSave.getBytes(charset);
    }
    catch (UnsupportedOperationException e) {
      return Magic8.NO_WAY;
    }
    if (bom != null && !ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave); // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    return !Arrays.equals(bytesToSave, bytes) ? Magic8.NO_WAY : loaded.equals(text) ? Magic8.ABSOLUTELY : Magic8.WELL_IF_YOU_INSIST;
  }

  static Magic8 isSafeToConvertTo(@NotNull VirtualFile virtualFile, @NotNull String text, @NotNull byte[] bytesOnDisk, @NotNull Charset charset) {
    try {
      String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
      String textToSave = lineSeparator.equals("\n") ? text : StringUtil.convertLineSeparators(text, lineSeparator);

      Pair<Charset, byte[]> chosen = LoadTextUtil.chooseMostlyHarmlessCharset(virtualFile.getCharset(), charset, textToSave);

      byte[] saved = chosen.second;

      CharSequence textLoadedBack = LoadTextUtil.getTextByBinaryPresentation(saved, charset);

      return !text.equals(textLoadedBack.toString()) ? Magic8.NO_WAY : Arrays.equals(saved, bytesOnDisk) ? Magic8.ABSOLUTELY : Magic8.WELL_IF_YOU_INSIST;
    }
    catch (UnsupportedOperationException e) { // unsupported encoding
      return Magic8.NO_WAY;
    }
  }

  static void saveIn(@NotNull final Document document,
                     final Editor editor,
                     @NotNull final VirtualFile virtualFile,
                     @NotNull final Charset charset) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    documentManager.saveDocument(document);
    final Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    boolean writable = project == null ? virtualFile.isWritable() : ReadonlyStatusHandler.ensureFilesWritable(project, virtualFile);
    if (!writable) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot save the file " + virtualFile.getPresentableUrl(), "Unable to Save", null);
      return;
    }

    EncodingProjectManagerImpl.suppressReloadDuring(() -> {
      EncodingManager.getInstance().setEncoding(virtualFile, charset);
      try {
        ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
          virtualFile.setCharset(charset);
          LoadTextUtil.write(project, virtualFile, virtualFile, document.getText(), document.getModificationStamp());
          return null;
        });
      }
      catch (IOException io) {
        Messages.showErrorDialog(project, io.getMessage(), "Error Writing File");
      }
    });
  }

  static void reloadIn(@NotNull final VirtualFile virtualFile, @NotNull final Charset charset) {
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();

    if (documentManager.getCachedDocument(virtualFile) == null) {
      // no need to reload document
      EncodingManager.getInstance().setEncoding(virtualFile, charset);
      return;
    }

    final Disposable disposable = Disposer.newDisposable();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(disposable);
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
        if (!file.equals(virtualFile)) return;
        Disposer.dispose(disposable); // disconnect

        EncodingManager.getInstance().setEncoding(file, charset);

        LoadTextUtil.setCharsetWasDetectedFromBytes(file, null);
      }
    });

    // if file was modified, the user will be asked here
    try {
      EncodingProjectManagerImpl.suppressReloadDuring(() -> ((VirtualFileListener)documentManager).contentsChanged(
        new VirtualFileEvent(null, virtualFile, virtualFile.getName(), virtualFile.getParent())));
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // returns file type description if the charset is hard-coded or null if file type does not restrict encoding
  private static String checkHardcodedCharsetFileType(@NotNull VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    // in lesser IDEs all special file types are plain text so check for that first
    if (fileType == FileTypes.PLAIN_TEXT) return null;
    if (fileType == StdFileTypes.GUI_DESIGNER_FORM) return "IDEA GUI Designer form";
    if (fileType == StdFileTypes.IDEA_MODULE) return "IDEA module file";
    if (fileType == StdFileTypes.IDEA_PROJECT) return "IDEA project file";
    if (fileType == StdFileTypes.IDEA_WORKSPACE) return "IDEA workspace file";

    if (fileType == StdFileTypes.PROPERTIES) return ".properties file\n(see Settings|Editor|File Encodings|Properties Files)";

    if (fileType == StdFileTypes.XML) {
      return "XML file";
    }
    if (fileType == StdFileTypes.JSPX) {
      return "JSPX file";
    }
    return null;
  }

  @NotNull
  // returns pair (existing charset (null means N/A); failReason: null means enabled, notnull means disabled and contains error message)
  public static Pair<Charset, String> checkCanReload(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return Pair.create(null, REASON_FILE_IS_A_DIRECTORY);
    }
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return Pair.create(null, REASON_BINARY_FILE);
    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    Charset existing = virtualFile.getCharset();
    String autoDetectedFrom = LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile);
    String failReason;
    if (autoDetectedFrom != null) {
      // no point changing encoding if it was auto-detected
      failReason = "the encoding was " + autoDetectedFrom;
    }
    else if (charsetFromContent != null) {
      failReason = REASON_HARDCODED_IN_TEXT;
      existing = charsetFromContent;
    }
    else {
      failReason = fileTypeDescriptionError(virtualFile);
    }
    return Pair.create(existing, failReason);
  }

  @Nullable
  private static String fileTypeDescriptionError(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getFileType().isBinary()) return REASON_BINARY_FILE;

    String fileTypeDescription = checkHardcodedCharsetFileType(virtualFile);
    return fileTypeDescription == null ? null : String.format(REASON_HARDCODED_FOR_FILE, fileTypeDescription);
  }

  @Nullable("null means enabled, notnull means disabled and contains error message")
  static String checkCanConvert(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return REASON_FILE_IS_A_DIRECTORY;
    }

    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    return charsetFromContent != null ? REASON_HARDCODED_IN_TEXT : fileTypeDescriptionError(virtualFile);
  }

  // null means enabled, (current charset, error description) otherwise
  @Nullable
  public static Pair<Charset, String> checkSomeActionEnabled(@NotNull VirtualFile selectedFile) {
    String saveError = checkCanConvert(selectedFile);
    if (saveError == null) return null;
    Pair<Charset, String> reloadResult = checkCanReload(selectedFile);
    String reloadError = reloadResult.second;
    if (reloadError == null) return null;
    String errorDescription = saveError.equals(reloadError) ? saveError : saveError + ", " + reloadError;
    return Pair.create(reloadResult.first, errorDescription);
  }
}
