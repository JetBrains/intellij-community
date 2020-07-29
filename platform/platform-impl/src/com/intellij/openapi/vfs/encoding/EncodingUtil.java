// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.AppTopics;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

public final class EncodingUtil {

  enum FailReason {
    IS_DIRECTORY,
    IS_BINARY,
    BY_FILE,
    BY_BOM,
    BY_BYTES,
    BY_FILETYPE
  }

  // the result of wild guess
  public enum Magic8 {
    ABSOLUTELY,
    WELL_IF_YOU_INSIST,
    NO_WAY
  }

  // check if file can be loaded in the encoding correctly:
  // returns ABSOLUTELY if bytes on disk, converted to text with the charset, converted back to bytes matched
  // returns NO_WAY if the new encoding is incompatible (bytes on disk will differ)
  // returns WELL_IF_YOU_INSIST if the bytes on disk remain the same but the text will change
  @NotNull
  static Magic8 isSafeToReloadIn(@NotNull VirtualFile virtualFile, @NotNull CharSequence text, byte @NotNull [] bytes, @NotNull Charset charset) {
    // file has BOM but the charset hasn't
    byte[] bom = virtualFile.getBOM();
    if (bom != null && !CharsetToolkit.canHaveBom(charset, bom)) return Magic8.NO_WAY;

    // the charset has mandatory BOM (e.g. UTF-xx) but the file hasn't or has wrong
    byte[] mandatoryBom = CharsetToolkit.getMandatoryBom(charset);
    if (mandatoryBom != null && !ArrayUtil.startsWith(bytes, mandatoryBom)) return Magic8.NO_WAY;

    String loaded = LoadTextUtil.getTextByBinaryPresentation(bytes, charset).toString();

    String separator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
    String toSave = StringUtil.convertLineSeparators(loaded, separator);

    LoadTextUtil.AutoDetectionReason failReason = LoadTextUtil.getCharsetAutoDetectionReason(virtualFile);
    if (failReason != null && StandardCharsets.UTF_8.equals(virtualFile.getCharset()) && !StandardCharsets.UTF_8.equals(charset)) {
      return Magic8.NO_WAY; // can't reload utf8-autodetected file in another charset
    }

    byte[] bytesToSave;
    try {
      bytesToSave = toSave.getBytes(charset);
    }
    // turned out some crazy charsets have incorrectly implemented .newEncoder() returning null
    catch (UnsupportedOperationException | NullPointerException e) {
      return Magic8.NO_WAY;
    }
    if (bom != null && !ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave); // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    return !Arrays.equals(bytesToSave, bytes) ? Magic8.NO_WAY : StringUtil.equals(loaded, text) ? Magic8.ABSOLUTELY : Magic8.WELL_IF_YOU_INSIST;
  }

  @NotNull
  static Magic8 isSafeToConvertTo(@NotNull VirtualFile virtualFile, @NotNull CharSequence text, byte @NotNull [] bytesOnDisk, @NotNull Charset charset) {
    try {
      String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
      CharSequence textToSave = lineSeparator.equals("\n") ? text : StringUtilRt.convertLineSeparators(text, lineSeparator);

      Pair<Charset, byte[]> chosen = LoadTextUtil.chooseMostlyHarmlessCharset(virtualFile.getCharset(), charset, textToSave.toString());

      byte[] saved = chosen.second;

      CharSequence textLoadedBack = LoadTextUtil.getTextByBinaryPresentation(saved, charset);

      return !StringUtil.equals(text, textLoadedBack) ? Magic8.NO_WAY : Arrays.equals(saved, bytesOnDisk) ? Magic8.ABSOLUTELY : Magic8.WELL_IF_YOU_INSIST;
    }
    catch (UnsupportedOperationException e) { // unsupported encoding
      return Magic8.NO_WAY;
    }
  }

  static void saveIn(@NotNull Project project,
                     @NotNull Document document,
                     Editor editor,
                     @NotNull VirtualFile virtualFile,
                     @NotNull Charset charset) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    documentManager.saveDocument(document);
    boolean writable = ReadonlyStatusHandler.ensureFilesWritable(project, virtualFile);
    if (!writable) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          IdeBundle.message("dialog.message.cannot.save.the.file.0", virtualFile.getPresentableUrl()),
                                          IdeBundle.message("dialog.title.unable.to.save"), null);
      return;
    }

    EncodingProjectManagerImpl.suppressReloadDuring(() -> {
      EncodingProjectManager.getInstance(project).setEncoding(virtualFile, charset);
      try {
        ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
          virtualFile.setCharset(charset);
          LoadTextUtil.write(project, virtualFile, virtualFile, document.getText(), document.getModificationStamp());
          return null;
        });
      }
      catch (IOException io) {
        Messages.showErrorDialog(project, io.getMessage(), IdeBundle.message("dialog.title.error.writing.file"));
      }
    });
  }

  static void reloadIn(@NotNull VirtualFile virtualFile,
                       @NotNull Charset charset,
                       @NotNull Project project) {
    Consumer<VirtualFile> setEncoding = file -> {
      EncodingProjectManager.getInstance(project).setEncoding(file, charset);
    };

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    if (documentManager.getCachedDocument(virtualFile) == null) {
      // no need to reload document
      setEncoding.accept(virtualFile);
      return;
    }

    final Disposable disposable = Disposer.newDisposable();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(disposable);
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void beforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) {
        if (!file.equals(virtualFile)) return;
        Disposer.dispose(disposable); // disconnect

        setEncoding.accept(file);

        LoadTextUtil.clearCharsetAutoDetectionReason(file);
      }
    });

    // if file was modified, the user will be asked here
    try {
      EncodingProjectManagerImpl.suppressReloadDuring(() -> {
        ((FileDocumentManagerImpl)documentManager).contentsChanged(new VFileContentChangeEvent(null, virtualFile, 0, 0, false));
      });
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
    if (fileType == ModuleFileType.INSTANCE) return "IDEA module file";
    if (fileType == ProjectFileType.INSTANCE) return "IDEA project file";
    if (fileType == WorkspaceFileType.INSTANCE) return "IDEA workspace file";

    if (fileType == StdFileTypes.PROPERTIES) return ".properties file\n(see Settings|Editor|File Encodings|Properties Files)";

    if (fileType == StdFileTypes.XML) {
      return "XML file";
    }
    if (fileType == StdFileTypes.JSPX) {
      return "JSPX file";
    }
    return null;
  }

  public static boolean canReload(@NotNull VirtualFile virtualFile) {
    return checkCanReload(virtualFile, null) == null;
  }

  @Nullable
  static FailReason checkCanReload(@NotNull VirtualFile virtualFile, @Nullable Ref<? super Charset> current) {
    if (virtualFile.isDirectory()) {
      return FailReason.IS_DIRECTORY;
    }
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return FailReason.IS_BINARY;
    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    Charset existing = virtualFile.getCharset();
    LoadTextUtil.AutoDetectionReason autoDetectedFrom = LoadTextUtil.getCharsetAutoDetectionReason(virtualFile);
    FailReason result;
    if (autoDetectedFrom != null) {
      // no point changing encoding if it was auto-detected
      result = autoDetectedFrom == LoadTextUtil.AutoDetectionReason.FROM_BOM ? FailReason.BY_BOM : FailReason.BY_BYTES;
    }
    else if (charsetFromContent != null) {
      result = FailReason.BY_FILE;
      existing = charsetFromContent;
    }
    else {
      result = fileTypeDescriptionError(virtualFile);
    }
    if (current != null) current.set(existing);
    return result;
  }

  @Nullable
  private static FailReason fileTypeDescriptionError(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getFileType().isBinary()) return FailReason.IS_BINARY;

    String fileTypeDescription = checkHardcodedCharsetFileType(virtualFile);
    return fileTypeDescription == null ? null : FailReason.BY_FILETYPE;
  }

  @Nullable("null means enabled, notnull means disabled and contains error message")
  static FailReason checkCanConvert(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return FailReason.IS_DIRECTORY;
    }

    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    return charsetFromContent != null ? FailReason.BY_FILE : fileTypeDescriptionError(virtualFile);
  }

  @Nullable
  static FailReason checkCanConvertAndReload(@NotNull VirtualFile selectedFile) {
    FailReason result = checkCanConvert(selectedFile);
    if (result == null) return null;
    return checkCanReload(selectedFile, null);
  }

  @Nullable
  public static Pair<Charset, String> getCharsetAndTheReasonTooltip(@NotNull VirtualFile file) {
    FailReason r1 = checkCanConvert(file);
    if (r1 == null) return null;
    Ref<Charset> current = Ref.create();
    FailReason r2 = checkCanReload(file, current);
    if (r2 == null) return null;
    String errorDescription = r1 == r2 ? reasonToString(r1, file) : reasonToString(r1, file) + ", " + reasonToString(r2, file);
    return Pair.create(current.get(), errorDescription);
  }

  static String reasonToString(@NotNull FailReason reason, VirtualFile file) {
    switch (reason) {
      case IS_DIRECTORY: return "disabled for a directory";
      case IS_BINARY: return "disabled for a binary file";
      case BY_FILE: return "charset is hard-coded in the file";
      case BY_BOM: return "charset is auto-detected by BOM";
      case BY_BYTES: return "charset is auto-detected from content";
      case BY_FILETYPE: return "disabled for " + file.getFileType().getDescription();
    }
    throw new AssertionError(reason);
  }
}
